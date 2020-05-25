import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.lang.acl.*;

import java.io.IOException;

public class BookBuyerAgent extends Agent {

    private String targetBookTitle;
    // może być dostarczane dynamicznie za pomocą DF
    private AID[] sellerAgents = {
//            new AID("seller1", AID.ISLOCALNAME),
            new AID("seller2", AID.ISLOCALNAME)};

    protected void setup() {
        //doWait(2000);

        System.out.println("Witam! Agent-kupiec " + getAID().getName() + " (wersja c lato, 2019/20) jest gotow!");

        Object[] args = getArguments(); // pobranie argumentów wejściowych - tytułów książek

        if (args != null && args.length > 0) {
            targetBookTitle = (String) args[0];
            System.out.println("Zamierzam kupic ksiazke zatytulowana " + targetBookTitle);

            addBehaviour(new RequestPerformer());
        } else {
            System.out.println("Nie podano tytulu ksiazki w argumentach wejsciowych");
            doDelete();
        }
    }

    protected void takeDown() {
        System.out.println("Agent-kupiec " + getAID().getName() + " konczy istnienie.");
    }


    /**
     * Inner class RequestPerformer.
     * This is the behaviour used by Book-buyer agents to request seller
     * agents the target book.
     */
    private class RequestPerformer extends Behaviour {
        final int BuyProposalSendingStep = 0;
        final int ReceiveResponsesStep = 1;
        final int NegotiateStep = 2;
        final int SendOfferToBestSellerStep = 3;
        final int ReceiveBuyOfferResponseStep = 4;
        final int OutOfBusiness = 5;

        private AID bestSeller;     // agent sprzedający z najkorzystniejszą ofertą
        private int bestPrice;      // najlepsza cena
        private int repliesCnt = 0; // liczba odpowiedzi od agentów
        private MessageTemplate mt; // szablon odpowiedzi
        private int step = 0;       // krok

        private int negotiationCounter = 0;

        public void action() {
            switch (step) {
                case BuyProposalSendingStep:
                    System.out.print(" Oferta kupna (CFP) jest wysylana do: ");
                    for (AID agent : sellerAgents) {
                        System.out.print(agent + " ");
                    }
                    System.out.println();

                    // Tworzenie wiadomości CFP do wszystkich sprzedawców:
                    ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                    for (AID sellerAgent : sellerAgents) {
                        cfp.addReceiver(sellerAgent);
                    }
                    cfp.setContent(targetBookTitle);
                    cfp.setConversationId("book-trade");
                    cfp.setReplyWith("cfp" + System.currentTimeMillis());
                    myAgent.send(cfp);

                    // Utworzenie szablonu do odbioru ofert sprzedaży tylko od wskazanych sprzedawców:
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("book-trade"),
                            MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
                    step = ReceiveResponsesStep;     // przejście do kolejnego kroku
                    break;
                case ReceiveResponsesStep:
                    ACLMessage reply = myAgent.receive(mt);
                    if (reply != null) {
                        if (reply.getPerformative() == ACLMessage.PROPOSE) {
                            int price = Integer.parseInt(reply.getContent());  // cena książki
                            if (bestSeller == null || price < bestPrice) {
                                bestPrice = price;
                                bestSeller = reply.getSender();
                            }
                        }
                        if (++repliesCnt >= sellerAgents.length) {
                            step = NegotiateStep;
                        }
                    } else {
                        block();
                    }
                    break;

                case NegotiateStep:
                    final int proposalCount = 6;
                    final int acceptanceThreshold = 3;
                    final int incrementSize = 6;

                    double currentPrice = 0;

                    ACLMessage priceProposal = new ACLMessage(ACLMessage.REFUSE);
                    priceProposal.addReceiver(bestSeller);
                    currentPrice = negotiationCounter == 0 ? bestPrice * 0.6 : currentPrice + incrementSize;

                    try {
                        priceProposal.setContentObject(new SellerOffer(targetBookTitle, currentPrice));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    System.out.println("The buying agent is not satisfied with current price");
                    System.out.println("\t trying for the: " + negotiationCounter + " time");
                    System.out.println("\t sending offer: " + currentPrice);
                    myAgent.send(priceProposal);

                    ACLMessage response = myAgent.receive(mt);
                    if (response != null && response.getPerformative() == ACLMessage.INFORM) {
                        int offer = Integer.parseInt(response.getContent());
                        System.out.println("Buyer received counter offer from seller with value: " + offer);
                        if (offer - currentPrice <= acceptanceThreshold) {
                            System.out.println("Negotiation succeeded with price" + currentPrice);
                            step = SendOfferToBestSellerStep;
                            break;
                        }

                        if (negotiationCounter == proposalCount) {
                            System.out.println("The buying agent failed to negotiate satisfying price, end.");
                            step = OutOfBusiness;
                            return;
                        }
                    }

                    negotiationCounter += 1;
                    block();
                    break;

                case SendOfferToBestSellerStep:      // wys�anie zam�wienia do sprzedawcy, kt�ry z�o�y� najlepsz� ofert�
                    ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                    order.addReceiver(bestSeller);
                    order.setContent(targetBookTitle);
                    order.setConversationId("book-trade");
                    order.setReplyWith("order" + System.currentTimeMillis());
                    myAgent.send(order);
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("book-trade"),
                            MessageTemplate.MatchInReplyTo(order.getReplyWith()));
                    step = ReceiveBuyOfferResponseStep;
                    break;
                case ReceiveBuyOfferResponseStep:
                    reply = myAgent.receive(mt);
                    if (reply != null) {
                        if (reply.getPerformative() == ACLMessage.INFORM) {
                            System.out.println("Tytul " + targetBookTitle + " został zamowiony.");
                            System.out.println("Cena = " + bestPrice);
                            myAgent.doDelete();
                        }
                        step = OutOfBusiness;
                    } else {
                        block();
                    }
                    break;
            }
        }

        public boolean done() {
            return ((step == SendOfferToBestSellerStep && bestSeller == null) || step == OutOfBusiness);
        }
    }
}
