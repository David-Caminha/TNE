package agents;

import java.util.LinkedList;
import java.util.Vector;
import java.util.concurrent.ThreadLocalRandom;

import agents.UberAgent.AuctionBehaviour;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.AchieveREInitiator;

public class BuyerAgent extends Agent{

	float limitPrice;
	float aggressiveness;
	float increaseRate;

	Agent agent;
	boolean bidding;
	boolean waiting;
	boolean done;
	
	int refuseTimeout;

	public void setup() {
		agent = this;
		bidding = false;
		waiting = false;
		done = false;
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

	float bidValue()
	{
		float bidValue = 0;
		float outstandingBid = AuctioneerAgent.outstandingBid;
		float targetPrice = 0;
		if(AuctioneerAgent.tradingRound == 1)
		{
			targetPrice = Math.min(AuctioneerAgent.outstandingAsk, limitPrice);
		}
		else
		{
			float eqPrice = equilibriumPrice();
			if(limitPrice < eqPrice) //extra-marginal
			{
				if(aggressiveness < 0)
					targetPrice = (float) (limitPrice * (1 - (Math.exp(-aggressiveness) - 1) / (Math.exp(1) - 1)));
				else
					targetPrice = limitPrice;
			}
			else //intra-marginal
			{
				if(aggressiveness < 0)
					targetPrice = (float) (eqPrice * (1 - (Math.exp(-aggressiveness) - 1) / (Math.exp(1) - 1)));
				else
					targetPrice = (float) (eqPrice + (limitPrice - eqPrice) * ((Math.exp(aggressiveness) - 1) / (Math.exp(1) - 1))); 
			}
		}
		bidValue = outstandingBid + (targetPrice - outstandingBid) / increaseRate;
		return bidValue;
	}

	int newBidProbability()
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

	boolean makeBid()
	{
		int randomNum = ThreadLocalRandom.current().nextInt(0, 101);
		int bidProb = newBidProbability();
		return randomNum < bidProb;
	}

	class AuctionBehaviour extends Behaviour {

		@Override
		public void action() {
			if(!bidding && !waiting && makeBid())
			{
				bidding = true;
				addBehaviour(new FIPARequestInitBid(agent, new ACLMessage(ACLMessage.REQUEST)));
			}
		}

		@Override
		public boolean done() {
			return (AuctioneerAgent.tradingRound > AuctioneerAgent.numRounds || done || refuseTimeout>3);
		}

	}

	class FIPARequestInitBid extends AchieveREInitiator {

		public FIPARequestInitBid(Agent a, ACLMessage msg) {
			super(a, msg);
		}

		protected Vector<ACLMessage> prepareRequests(ACLMessage msg) {
			Vector<ACLMessage> v = new Vector<ACLMessage>();
			msg.addReceiver(new AID("auctioneer", false)); //Get receivers dynamically
			String message = "BID " + bidValue();
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
			bidding = false;
		}

		protected void handleInform(ACLMessage inform) {
			System.out.println(inform);
			String msgContent = inform.getContent();
			if(msgContent.contains("A transaction was made with the value"))
			{
				String[] parts = msgContent.split(":");
				float transactionValue = Float.parseFloat(parts[1]);
				AuctioneerAgent.buyerTransactions.addLast(transactionValue);
				done = true;
			}
			else if(msgContent.equals("A new offer has been registered!"))
			{
				addBehaviour(new InformListeningBehaviour());
				waiting = true;
				bidding = false;
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
					waiting = false;
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
