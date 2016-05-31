package agents;

import java.util.LinkedList;
import java.util.Vector;
import java.util.concurrent.ThreadLocalRandom;

import agents.BuyerAgent.FIPARequestInitBid;
import agents.BuyerAgent.InformListeningBehaviour;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.AchieveREInitiator;

public class UberAgent extends Agent{
	
	float limitPrice;
	float aggressiveness;
	float increaseRate;
	float maxMarketPrice;
	
	Agent agent;
	boolean asking;
	boolean waiting;
	boolean done;
	
	int refuseTimeout;
	
	public void setup() {
		agent = this;
		asking = false;
		waiting = false;
		done = false;
		maxMarketPrice = AuctioneerAgent.maxMarketPrice;
		Object[] args = getArguments();
		limitPrice = (float) args[0];
		aggressiveness = (float) args[1];
		increaseRate = (float) args[2];

		if(aggressiveness > 1)
			aggressiveness = 1;
		if(aggressiveness < -1)
			aggressiveness = -1;
		if(increaseRate < 1)
			increaseRate = 1;
		
		refuseTimeout = 0;

		
		addBehaviour(new AuctionBehaviour());
	}
	
	public void takeDown() {
		System.out.println(getLocalName() + ": done working.");
	}

	Float equilibriumPrice()
	{
		LinkedList<Float> latestTransactions = new LinkedList<>(AuctioneerAgent.latestTransactions);
		if(latestTransactions.size() == 0)
			return null;
		else
		{
			Float eqPrice = 0.0f;
			float weight = 1.0f;
			for(Float transaction : latestTransactions)
			{
				eqPrice += transaction * weight;
				weight *= 0.9f;
			}
			eqPrice = eqPrice / latestTransactions.size();
			return eqPrice;
		}
	}
	
	float askValue() 
	{
		float askValue = 0;
		float outstandingAsk = AuctioneerAgent.outstandingAsk;
		float targetPrice = 0;
		if(AuctioneerAgent.tradingRound == 1)
		{
			targetPrice = Math.max(AuctioneerAgent.outstandingBid, limitPrice);
		}
		else
		{
			float eqPrice = equilibriumPrice();
			if(limitPrice < eqPrice) //extra-marginal
			{
				if(aggressiveness < 0)
					targetPrice = (float) (limitPrice + (maxMarketPrice - limitPrice) * ((Math.exp(-aggressiveness) - 1) / (Math.exp(1) - 1)));
				else
					targetPrice = limitPrice;
			}
			else //intra-marginal
			{
				if(aggressiveness < 0)
					targetPrice = (float) (eqPrice + (maxMarketPrice - eqPrice) * ((Math.exp(-aggressiveness) - 1) / (Math.exp(1) - 1)));
				else
					targetPrice = (float) (limitPrice + (eqPrice - limitPrice) * (1 - (Math.exp(aggressiveness) - 1) / (Math.exp(1) - 1))); 
			}
		}
		askValue = outstandingAsk - (outstandingAsk - targetPrice) / increaseRate;
		return askValue;
	}

	int newAskProbability()
	{
		if(aggressiveness < -0.5f)
			return 40;
		else if(aggressiveness < 0)
			return 55;
		else if(aggressiveness < 0.5f)
			return 70;
		else
			return 85;
	}
	
	boolean makeAsk()
	{
		int randomNum = ThreadLocalRandom.current().nextInt(0, 101);
		int askProb = newAskProbability();
		return randomNum < askProb;
	}
	
	class AuctionBehaviour extends Behaviour {

		@Override
		public void action() {
			if(!asking && !waiting && makeAsk())
			{
				asking = true;
				addBehaviour(new FIPARequestInitAsk(agent, new ACLMessage(ACLMessage.REQUEST)));
			}
		}

		@Override
		public boolean done() {
			return (AuctioneerAgent.tradingRound > AuctioneerAgent.numRounds || done);
		}
		
	}
	
	class FIPARequestInitAsk extends AchieveREInitiator {

		public FIPARequestInitAsk(Agent a, ACLMessage msg) {
			super(a, msg);
		}

		protected Vector<ACLMessage> prepareRequests(ACLMessage msg) {
			Vector<ACLMessage> v = new Vector<ACLMessage>();
			msg.addReceiver(new AID("auctioneer", false)); //Get receivers dinamically
			String message = "ASK " + askValue();
			msg.setContent(message);
			v.add(msg);
			return v;
		}
		
		protected void handleAgree(ACLMessage agree) {
			System.out.println(agree);
			refuseTimeout = 0;
		}
		
		protected void handleRefuse(ACLMessage refuse) {
			System.out.println(refuse);
			refuseTimeout++;
			asking = false;
		}
		
		protected void handleInform(ACLMessage inform) {
			System.out.println(inform);
			String msgContent = inform.getContent();
			if(msgContent.contains("A transaction was made with the value"))
			{
				String[] parts = msgContent.split(":");
				float transactionValue = Float.parseFloat(parts[1]);
				AuctioneerAgent.uberTransactions.addLast(transactionValue);
				AuctioneerAgent.taxiTransactions.addLast(0.0f);
				done = true;
			}
			else if(msgContent.equals("A new offer has been registered!"))
			{
				addBehaviour(new InformListeningBehaviour());
				waiting = true;
				asking = false;
			}
		}
		
		protected void handleFailure(ACLMessage failure) {
			System.out.println(failure);
		}
	}

	class InformListeningBehaviour extends Behaviour {

		MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);

		@Override
		public void action() {
			ACLMessage msg = receive(mt);
			if(msg != null)
			{
				System.out.println(msg);
				String msgContent = msg.getContent();
				if(msgContent.contains("A transaction was made with the value"))
				{
					String[] parts = msgContent.split(":");
					float transactionValue = Float.parseFloat(parts[1]);
					AuctioneerAgent.buyerTransactions.addLast(transactionValue);
					done = true;
				}
				else if(msgContent.equals("A new offer has been registered!"))
				{

					waiting = false;
				}
			}
			else
			{
				block();
			}
		}

		@Override
		public boolean done() {
			return !waiting;
		}
	}
}
