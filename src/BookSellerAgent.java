import jade.core.Agent;
import jade.core.behaviours.*;
import jade.lang.acl.*;

import java.util.*;
import java.lang.*;


public class BookSellerAgent extends Agent {
    private Hashtable<String, Integer> catalogue;

    protected void setup() {
        catalogue = new Hashtable<>();

        Random randomGenerator = new Random();

        catalogue.put("Zamek", 280 + randomGenerator.nextInt(220));
        catalogue.put("Proces", 200 + randomGenerator.nextInt(170));
        catalogue.put("Opowiadania", 110 + randomGenerator.nextInt(50));
        catalogue.put("JADE dla opornych", 120 + randomGenerator.nextInt(140));
        catalogue.put("Poniedzialek", 270 + randomGenerator.nextInt(80));

        doWait(2000);

        System.out.println("Witam! Agent-sprzedawca (wersja c lato,2019/20) " + getAID().getName() + " jest gotow do sprzedazy");

        // Dodanie zachowania obsugujcego odpowiedzi na oferty klientów (kupujcych książki):
        addBehaviour(new OfferRequestsServer());

        // Dodanie zachowania obsugujcego zamwienie klienta:
        addBehaviour(new PurchaseOrdersServer());
    }

    // Metoda realizujca zakoczenie pracy agenta:
    protected void takeDown() {
        System.out.println("Agent-sprzedawca (wersja c lato,2029/20) " + getAID().getName() + " zakoczy działalnosc.");
    }

    /**
     * Inner class OfferRequestsServer.
     * This is the behaviour used by Book-seller agents to serve incoming requests
     * for offer from buyer agents.
     * If the requested book is in the local catalogue the seller agent replies
     * with a PROPOSE message specifying the price. Otherwise a REFUSE message is
     * sent back.
     */
    class OfferRequestsServer extends CyclicBehaviour {

        MessageTemplate mt;

        public void action() {
            // Tworzenie szablonu wiadomoci
            mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
            // Próba odbioru wiadomoci zgodnej z szablonem:
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null) {
                String title = msg.getContent();  // odczytanie tytułu

                System.out.println("Agent-sprzedawca " + getAID().getName() + " otrzymal prosbe o cene: " + title);
                // tworzenie i wysylanie odpowiedzi
                {
                    ACLMessage reply = msg.createReply();
                    Integer price = catalogue.get(title);
                    if (price != null) {
                        reply.setPerformative(ACLMessage.PROPOSE);
                        reply.setContent(String.valueOf(price.intValue()));

                        System.out.println("Agent-sprzedawca " + getAID().getName() + " odpowiada: " + price);
                    } else {
                        reply.setPerformative(ACLMessage.REFUSE);
                        reply.setContent("Agent-sprzedawca (wersja c lato,2019/20) " + getAID().getName() + "Tytul jest niedostepny");
                    }
                    myAgent.send(reply);
                }
            } else {
                block(); // blokowanie do czsau nadejścia nowej wiadomości
            }
        }
    }

    class PurchaseOrdersServer extends CyclicBehaviour {
        Map<String, Double> buyersLastOffers = new HashMap<>();

        private String replyWith;
        MessageTemplate mt = MessageTemplate.or(
                MessageTemplate.MatchPerformative(ACLMessage.PROPOSE),
                MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL));

        public void action() {
            ACLMessage msg = myAgent.receive(mt);

            if ((msg != null)) {
                // jesli klient zgodzil sie na nasza propozycje
                if (msg.getPerformative() == ACLMessage.ACCEPT_PROPOSAL) {
                    ACLMessage reply = msg.createReply();
                    String title = msg.getContent();
                    reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                    System.out.println(
                            "Agent-sprzedawca (wersja c lato,2019/20) " + getAID().getName() + " sprzedal ksiazke o tytule: " + title);
                    myAgent.send(reply);
                } else { // Kupiec zlozyl kontroferte
                    String title = msg.getConversationId();
                    double theirOffer = Double.parseDouble(msg.getContent());

                    String key = msg.getSender().getName() + title;

                    boolean isFirstOffer = !buyersLastOffers.containsKey(key);
                    double offer = isFirstOffer
                            ? (catalogue.get(title) + theirOffer) / 2
                            : (buyersLastOffers.get(msg.getSender().getName() + title) + theirOffer) / 2;

                    ACLMessage response = msg.createReply();
                    response.setReplyWith(msg.getReplyWith());
                    response.setContent(String.valueOf(offer));
                    myAgent.send(response);
                    // zapisywanie wyslanej propozycji
                    buyersLastOffers.put(key, offer);

                    System.out.println("Agent-sprzedawca (wersja c lato,2019/20) "
                            + getAID().getName() + "Zaproponowal nowa cene: " + offer);
                }
                block();
            }
        }
    }
}