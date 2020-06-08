import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.lang.acl.*;

public class BookBuyerAgent extends Agent {

    private String targetBookTitle;
    // może być dostarczane dynamicznie za pomocą DF
    private AID[] sellerAgents = {
//            new AID("seller1", AID.ISLOCALNAME),
            new AID("seller2", AID.ISLOCALNAME)};

    protected void setup() {
        //doWait(2000);

        System.out.println("Witam!  Agent-kupiec " + getAID().getName() + " (wersja c lato, 2019/20) jest gotow!");

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
        System.out.println(" Agent-kupiec " + getAID().getName() + " konczy istnienie.");
    }


    /**
     * Inner class RequestPerformer.
     * This is the behaviour used by Book-buyer agents to request seller
     * agents the target book.
     */
    private class RequestPerformer extends Behaviour {
        final int BuyProposalSendingStep = 0;
        final int ReceiveResponsesStep = 1;
        final int FirstOfferStep = 2;
        final int NegotiateStep = 3;
        final int SendOfferToBestSellerStep = 4;
        final int ReceiveBuyOfferResponseStep = 5;
        final int OutOfBusiness = 6;

        private AID bestSeller;     // agent sprzedający z najkorzystniejszą ofertą
        private int bestPrice;      // najlepsza cena
        private int repliesCnt = 0; // liczba odpowiedzi od agentów
        private MessageTemplate mt; // szablon odpowiedzi
        private int step = 0;       // krok


        int proposalCounter = 0;
        double lastOffer = 0;

        public void action() {
            switch (step) {
                case BuyProposalSendingStep: {
                    System.out.print(" Agent-kupiec" + getAID().getName() + " Oferta kupna (CFP) jest wysylana do: ");
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
                }
                case ReceiveResponsesStep: {
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
                            step = FirstOfferStep;
                        }
                    } else {
                        block();
                    }
                    break;
                }

                case FirstOfferStep: {
                    ACLMessage firstOffer = new ACLMessage(ACLMessage.PROPOSE);
                    firstOffer.addReceiver(bestSeller);
                    firstOffer.setConversationId(targetBookTitle);
                    double offer = bestPrice * 0.4;
                    lastOffer = offer;
                    firstOffer.setReplyWith("offer" + System.currentTimeMillis());
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId(firstOffer.getConversationId()),
                            MessageTemplate.MatchReplyWith(firstOffer.getReplyWith()));
                    firstOffer.setContent(String.valueOf(offer));
                    System.out.println(" Agent-kupiec" + getAID().getName() + " po raz pierwszy proponuje: " + offer);
                    myAgent.send(firstOffer);
                    step = NegotiateStep;
                    block();
                }

                case NegotiateStep: {
                    final int proposalLimit = 6;
                    final int acceptanceThreshold = 3;
                    final int incrementSize = 6;

                    ACLMessage response = myAgent.receive(mt);

                    if (response == null) {
                        break;
                    }

                    double counterOffer = Double.parseDouble(response.getContent());
                    boolean negotiationSucceeded = counterOffer - lastOffer < acceptanceThreshold;
                    if (negotiationSucceeded) {
                        System.out.println(" Agent-kupiec" + getAID().getName() + " Udalo sie wynegocjowac cene" + lastOffer);
                        step = SendOfferToBestSellerStep;
                        break;
                    } else {
                        proposalCounter++;
                        if (proposalCounter >= proposalLimit) {
                            System.out.println(
                                    " Agent-kupiec" + getAID().getName() + " Nie udalo sie wynegocjowac akceptowalnej ceny, ostatnia: " + lastOffer);
                            step = OutOfBusiness;
                            break;
                        }
                    }

                    double offer = lastOffer + incrementSize;
                    lastOffer = offer;
                    ACLMessage nextOffer = response.createReply();
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId(nextOffer.getConversationId()),
                            MessageTemplate.MatchReplyWith(nextOffer.getReplyWith()));
                    nextOffer.setContent(String.valueOf(offer));
                    nextOffer.setPerformative(ACLMessage.PROPOSE);
                    System.out.println(" Agent-kupiec" + getAID().getName() + " proponuje: " + offer + "po raz: " + proposalCounter);
                    myAgent.send(nextOffer);
                    block();
                    break;
                }

                case SendOfferToBestSellerStep: {
                    ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                    order.addReceiver(bestSeller);
                    order.setContent(targetBookTitle);
                    order.setReplyWith("order" + System.currentTimeMillis());
                    myAgent.send(order);
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("book-trade"),
                            MessageTemplate.MatchInReplyTo(order.getReplyWith()));
                    step = ReceiveBuyOfferResponseStep;
                    block();
                    break;
                }

                case ReceiveBuyOfferResponseStep: {
                    mt = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
                    ACLMessage reply = myAgent.receive(mt);
                    if (reply != null) {
                        if (reply.getPerformative() == ACLMessage.INFORM) {
                            System.out.println(" Agent-kupiec" + getAID().getName() + " Tytul " + targetBookTitle + " został zamowiony.");
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
        }

        public boolean done() {
            boolean isDone = (step == SendOfferToBestSellerStep && bestSeller == null) || step == OutOfBusiness;
            if (isDone) {
                System.out.println(myAgent.getName() + "is done with this business");
            }
            return isDone;
        }
    }
}
