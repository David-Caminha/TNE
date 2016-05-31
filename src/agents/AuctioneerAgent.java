package agents;

import java.util.LinkedList;

import jade.core.AID;
import jade.core.Agent;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.AchieveREResponder;

public class AuctioneerAgent extends Agent{

	static LinkedList<Float> buyerTransactions;
	static LinkedList<Float> uberTransactions;
	static LinkedList<Float> taxiTransactions;
	static LinkedList<Float> latestTransactions;
	static int tradingRound; //Which trading round is at the moment
	static float outstandingBid; //Best bid at the moment
	static float outstandingAsk; //Best ask at the moment
	static float delta; //Minimum increment for bid/ask
	static float maxMarketPrice; //Maximum price for a transaction in the market
	static int numRounds; //Number of rounds to trade for

	AID buyerName;
	AID sellerName;
	int status;

	protected void setup()
	{
		latestTransactions = new LinkedList<>();
		buyerTransactions = new LinkedList<>();
		uberTransactions = new LinkedList<>();
		taxiTransactions = new LinkedList<>();
		
		Object[] args = getArguments();
		delta = (float) args[0];
		maxMarketPrice = (float) args[1];
		numRounds = (int) args[2];
		
		tradingRound = 1;
		outstandingBid = 0;
		outstandingAsk = maxMarketPrice;
		buyerName = null;
		sellerName = null;
		status = 0;
		
		addBehaviour(new FIPARequestResp(this, MessageTemplate.MatchPerformative(ACLMessage.REQUEST)));
	}
	
	void startNewRound()
	{
		tradingRound++;
		System.out.println("------------ Starting trading round number " + tradingRound + " ------------");
		outstandingBid = 0;
		outstandingAsk = Integer.MAX_VALUE;
		buyerName = null;
		sellerName = null;
		status = 0;
	}

	class FIPARequestResp extends AchieveREResponder {

		public FIPARequestResp(Agent a, MessageTemplate mt) {
			super(a, mt);
		}

		protected ACLMessage handleRequest(ACLMessage request) {
			status = 0;
			System.out.println(request); //Get value for bid/ask
			//Check value is correct
			String content = request.getContent();
			String[] parts = content.split(" ");
			if(parts[0].equals("BID"))
			{
				float bid = Float.parseFloat(parts[1]);
				if(bid <= outstandingBid)
					status = 1;
				else if(bid < outstandingAsk)
				{
					status = 2;
					outstandingBid = bid;
				}
				else
				{
					status = 3;
					buyerName = request.getSender();
					outstandingBid = bid;
				}
			}
			else if(parts[0].equals("ASK"))
			{
				float ask = Float.parseFloat(parts[1]);
				if(ask >= outstandingAsk)
					status = 1;
				else if(ask > outstandingBid)
				{
					status = 2;
					outstandingAsk = ask;
				}
				else
				{
					status = 3;
					sellerName = request.getSender();
					outstandingAsk = ask;
				}
			}
			ACLMessage reply = request.createReply();
			if(status == 0)
			{
				reply.setPerformative(ACLMessage.FAILURE);
				reply.setContent("There was an unexpected error in your message.");
			}
			if(status == 1)
			{
				reply.setPerformative(ACLMessage.REFUSE);
				reply.setContent("There is a better offer in the market.");
			}
			else
			{
				reply.setPerformative(ACLMessage.AGREE);
				if(status == 2)
					reply.setContent("Your offer was saved.");
				else
					reply.setContent("Your offer was accepted a transaction is being made.");
			}
			return reply;
		}

		protected ACLMessage prepareResultNotification(ACLMessage request, ACLMessage response) {
			String responseContent = response.getContent();
			ACLMessage result = request.createReply();
			result.setPerformative(ACLMessage.INFORM);
			if(responseContent.equals("Your offer was accepted a transaction is being made."))
			{
				if(request.getSender() == buyerName)
					result.addReceiver(sellerName);
				else if(request.getSender() == sellerName)
					result.addReceiver(buyerName);
				float transactionValue = (outstandingAsk + outstandingBid) / 2.0f;
				result.setContent("A transaction was made with the value:" + transactionValue);
				latestTransactions.addFirst(transactionValue);
				if(latestTransactions.size() > 10)
					latestTransactions.removeLast();
				startNewRound();
			}
			else
			{
				String content = request.getContent();
				String[] parts = content.split(" ");
				if(parts[0].equals("BID"))
				{
					if(buyerName != null)
						result.addReceiver(buyerName);
					buyerName = request.getSender();
				}
				else if(parts[0].equals("ASK"))
				{
					if(sellerName != null)
						result.addReceiver(sellerName);
					sellerName = request.getSender();
				}
				result.setContent("A new offer has been registered!");
			}
			return result;
		}
	}
}
