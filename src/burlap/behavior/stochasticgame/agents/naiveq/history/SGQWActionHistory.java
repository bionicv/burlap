package burlap.behavior.stochasticgame.agents.naiveq.history;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import burlap.behavior.statehashing.DiscreteStateHashFactory;
import burlap.behavior.statehashing.StateHashFactory;
import burlap.behavior.statehashing.StateHashTuple;
import burlap.behavior.stochasticgame.agents.naiveq.SGQLAgent;
import burlap.behavior.stochasticgame.agents.naiveq.SGQValue;
import burlap.oomdp.core.Attribute;
import burlap.oomdp.core.Domain;
import burlap.oomdp.core.ObjectClass;
import burlap.oomdp.core.ObjectInstance;
import burlap.oomdp.core.State;
import burlap.oomdp.stochasticgames.Agent;
import burlap.oomdp.stochasticgames.GroundedSingleAction;
import burlap.oomdp.stochasticgames.JointAction;
import burlap.oomdp.stochasticgames.SGDomain;


/**
 * A Tabular Q-learning [1] algorithm for stochastic games formalisms that augments states with the actions each agent took in n
 * previous time steps. 
 * 
 * <p/>
 * 1. Watkins, Christopher JCH, and Peter Dayan. "Q-learning." Machine learning 8.3-4 (1992): 279-292. <br/>
 * @author James MacGlashan
 *
 */
public class SGQWActionHistory extends SGQLAgent {

	
	/**
	 * the joint action history
	 */
	protected LinkedList <JointAction>			history;
	
	/**
	 * The size of action history to store.
	 */
	protected int								historySize;
	
	/**
	 * a map from actions to int values which can be used to fill in an action history attribute value
	 */
	protected ActionIdMap						actionMap;
	
	
	/**
	 * The object class that will be used to represent a history component. A history component
	 * consists a player identifier, the action that player took, and how long ago that action was taken. A object
	 * instance of this class will be created for each player in the world and for each of the n time steps
	 * that this learning algorithm is told to keep.
	 */
	protected ObjectClass						classHistory;
	
	
	/**
	 * A constant for the name of the history time index attribute. For instance, a history object representing the
	 * action of an agent in the previous time step will have a value of 1 for this attribute
	 */
	public static final String					ATTHNUM = "histNum";
	
	/**
	 * A constant for the name of the attribute used to define which agent in the world this history object represents
	 */
	public static final String					ATTHPN = "histPN";
	
	/**
	 * A constant for the name of the attribute used to define which action an agent took
	 */
	public static final String					ATTHAID = "histAID";
	
	
	/**
	 * A constant for the name of the history object class. 
	 */
	public static String						CLASSHISTORY = "histAID";
	
	
	
	/**
	 * Initializes the learning algorithm using 0.1 epsilon greedy learning strategy/policy
	 * @param d the domain in which the agent will act
	 * @param discount the discount factor
	 * @param learningRate the learning rate
	 * @param hashFactory the state hashing factory to use
	 * @param historySize the number of previous steps to remember and with which to augment the state space
	 * @param maxPlayers the maximum number of players that will be in the game
	 * @param actionMap a mapping from actions to integer identifiers for them
	 */
	public SGQWActionHistory(SGDomain d, double discount, double learningRate, StateHashFactory hashFactory, int historySize, int maxPlayers, ActionIdMap actionMap) {
		super(d, discount, learningRate, hashFactory);
		this.historySize = historySize;
		this.actionMap = actionMap;
		
		
		//set up history augmentation object information
		Domain augmentingDomain = new SGDomain();
		
		Attribute histNum = new Attribute(augmentingDomain, ATTHNUM, Attribute.AttributeType.DISC);
		histNum.setDiscValuesForRange(0, historySize-1, 1);
		
		Attribute histPN = new Attribute(augmentingDomain, ATTHPN, Attribute.AttributeType.DISC);
		histPN.setDiscValuesForRange(0, maxPlayers-1, 1);
		
		Attribute histAID = new Attribute(augmentingDomain, ATTHAID, Attribute.AttributeType.DISC);
		histAID.setDiscValuesForRange(0, actionMap.maxValue(), 1); //maxValue is when it the action is undefined from no history occurance
		
		classHistory = new ObjectClass(augmentingDomain, CLASSHISTORY);
		classHistory.addAttribute(histNum);
		classHistory.addAttribute(histPN);
		classHistory.addAttribute(histAID);
		
		
		List <Attribute> attsForHistoryHashing = new ArrayList<Attribute>();
		attsForHistoryHashing.add(histNum);
		attsForHistoryHashing.add(histPN);
		attsForHistoryHashing.add(histAID);
		
		//ugly, but not sure how to resolve at the moment...
		if(this.hashFactory instanceof DiscreteStateHashFactory){
			((DiscreteStateHashFactory) this.hashFactory).setAttributesForClass(CLASSHISTORY, attsForHistoryHashing);
		}
	}

	@Override
	public void gameStarting() {
		this.history = new LinkedList<JointAction>();
	}
	
	
	
	@Override
	public void observeOutcome(State s, JointAction jointAction, Map<String, Double> jointReward, State sprime, boolean isTerminal) {
		
		GroundedSingleAction myAction = jointAction.action(worldAgentName);
		SGQValue qe = this.getSGQValue(s, myAction);
		
		State augS = this.getHistoryAugmentedState(s);
		
		//update history
		if(history.size() == historySize){
			history.removeLast();
		}
		history.addFirst(jointAction);
		
		State augSP = this.getHistoryAugmentedState(sprime);
		
		
		if(internalRewardFunction != null){
			jointReward = internalRewardFunction.reward(augS, jointAction, augSP);
		}
		
		
		double r = jointReward.get(worldAgentName);
		double maxQ = 0.;
		if(!isTerminal){
			maxQ = this.getMaxQValue(sprime); //no need to use augmented states because the method will implicitly get them from the state hash call
		}
		

		qe.q = qe.q + this.learningRate * (r + (this.discount * maxQ) - qe.q);

		
	}
	
	
	/**
	 * Takes an input state and returns an augmented state with the history of actions each agent previously took.
	 * @param s the input state to augment
	 * @return an augmented state with the history of actions each agent previously took.
	 */
	protected State getHistoryAugmentedState(State s){
		
		State augS = s.copy();
		
		int h = 0;
		for(JointAction ja : history){
			
			for(GroundedSingleAction gsa : ja){
				augS.addObject(this.getHistoryObjectInstanceForAgent(gsa, h));
			}
			
			h++;
		}
		
		if(h < this.historySize){
			List <Agent> agents = world.getRegisteredAgents();
			while(h < this.historySize){
				for(Agent a : agents){
					augS.addObject(this.getHistoryLessObjectInstanceForAgent(a.getAgentName(), h));
				}
				h++;
			}
		}
		
		
		return augS;
		
	}
	
	
	/**
	 * Returns a history object instance for the corresponding action and how far back in history it occurred
	 * @param gsa the action that was taken (which includes which agent took it)
	 * @param h how far back in history the action was taken.
	 * @return a history object instance for the corresponding action and how far back in history it occurred
	 */
	protected ObjectInstance getHistoryObjectInstanceForAgent(GroundedSingleAction gsa, int h){
		
		String aname = gsa.actingAgent;
		
		ObjectInstance o = new ObjectInstance(classHistory, aname + "-h" + h);
		o.setValue(ATTHNUM, h);
		o.setValue(ATTHPN, world.getPlayerNumberForAgent(aname));
		o.setValue(ATTHAID, actionMap.getActionId(gsa));
		
		return o;
		
	}
	
	
	/**
	 * Returns a history object instance for a given agent in which the action that was taken is unset because
	 * the episode has not last h steps.
	 * @param aname the name of agent for which the history object should be returned
	 * @param h how many step backs this object instance represents
	 * @return a history object instance
	 */
	protected ObjectInstance getHistoryLessObjectInstanceForAgent(String aname, int h){
		
		ObjectInstance o = new ObjectInstance(classHistory, aname + "-h" + h);
		o.setValue(ATTHNUM, h);
		o.setValue(ATTHPN, world.getPlayerNumberForAgent(aname));
		o.setValue(ATTHAID, actionMap.maxValue());
		
		return o;
		
	}
	
	@Override
	protected StateHashTuple stateHash(State s){
		State augS = this.getHistoryAugmentedState(this.storedMapAbstraction.abstraction(s));
		return hashFactory.hashState(augS);
	}

}
