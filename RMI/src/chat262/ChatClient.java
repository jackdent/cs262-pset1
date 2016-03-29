package chat262;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.lang.*;



/**
 * PushWriter is our mechanism for push notifications from the server to clients.
 * It is technically a server that lives on the chat "client".  We push a handle
 * to it to the "server", so that when the chat "server" makes a method call on
 * it, like .recieve(), it will make an RMI call to the chat "client".
 * PushWriter implements the simple PushReciever protocol, with one simple method,
 * .recieve(List<Message> msgs).  This method will be invoked on the "client"
 * in its own thread.
 * When a PushWriter receives a message, it always writes it to standard out.
 * PushWriter has a string of the user for whom it's listening.  This user does
 * not change throughout the lifecycle of the PushWriter; if the user logs out
 * and a new user logs in, the program must create a new PushWriter for them.
 * The PushWriter needs to know which user it's receiving for because it can
 * receive messages not directly to its user, such as messages to groups.
 * It will always write the message to stdout, but if the message is addressed
 * to the receiving user, it will omit the "to [username]" to be more succinct.
 */

class PushWriter implements PushReciever {
    /**
     * the username of the user whose push notifications we're subscribed to.
     */
    private final String listeningFor;

    /**
     * @param listeningFor 
     */
    public PushWriter(String listeningFor) {
        this.listeningFor = listeningFor;
    }

    /**
     * Called from the chat "server"
     * @param msgs a batched list of Messages, instead of a single message, to
     * reduce communication.  Multiple message delivery is also a feature we want
     * for when messages are queued because we've been offline.  By batching
     * their delivery, we don't have to send them sequentially.  This is a win
     * for simplified error handling and speed.  If we sent each message in a
     * queue sequentially, we'd have to wait for each send to complete before
     * sending the next message, which would cost an extra 
     * (number of messages - 1) * round trip latency.
     */
    @Override
    public void recieve(List<Message> msgs) {
        for (Message m : msgs) {
            String from = m.from;
            if (!m.to.equals(listeningFor)) {
                from += " to " + m.to;
            }
            System.out.println(from + ": " + m.msg);
        }
    }
}

public class ChatClient {
    public static void main(String[] args) throws Exception {
	String host = (args.length < 1) ? null : args[0];
        final Protocol262 server = getServer(host);
        System.out.println("connected to server");

        /*
        The client maintains state for which user is logged in and with whom
        they're currently chatting.  There is no authentication protocol beyond
        the user declaring who they are via the "/login" command.  This user
        is stored as the username in the variable currentUser.
        */
        String currentUser = null;

        /*
        The client keeps track of who the current conversation is with so the
        user can write a non-command line and send it as part of a conversation
        without having to identify the message recipient each time.  We keep
        track of the recipient as a username|groupname in currentRoom.
        */
        String currentRoom = null;
        
        /*
        We keep a reference to the current PushWriter so we can turn it off later
        if we logout (and log in as a different user).
        */
        PushWriter listener = null;

        /*
        Each line of stdin is a command, so we iterate through the lines of
        stdin and dispatch the command for each line.
        */
        Scanner input = new Scanner(System.in);
        while (input.hasNext()) {
            String line = input.nextLine();
            
            if (line.startsWith("/")) {
                try {
                    String[] command = line.split(" ");
                    switch (command[0]) {
                        case "/adduser":
                            server.createAccount(command[1]);
                            break;

                        case "/group":
                            List<String> members = Arrays.asList(Arrays.copyOfRange(command, 2, command.length));
                            server.createGroup(command[1], new HashSet<>(members));
                            break;

                        case "/listusers": {
                            String filter = command.length > 1 ? command[1] : null;
                            for (String name : server.listAccounts(filter)) {
                                System.out.println("> " + name);
                            }
                            break;
                        }

                        case "/listgroups": {
                            String filter = command.length > 1 ? command[1] : null;
                            for (String name : server.listGroups(filter)) {
                                System.out.println("> " + name);
                            }
                            break;
                        }

                        case "/login": {
                            final String user = command[1];
                            try {
                                server.createAccount(user);
                            } catch (IllegalArgumentException e) {
                                // user already exists
                            }
                            if (listener != null && currentUser != null) {
                                server.unsetListener(currentUser, listener);
                            }

                            listener = new PushWriter(user);
                            PushReciever stub = (PushReciever)UnicastRemoteObject.exportObject(listener, 0);
                            try {
                                server.setListener(user, stub);
                                currentUser = user;

                            } catch (Exception e) {
                                listener = null;
                                currentUser = null;
                            }

                            break;
                        }

                        case "/leaveforever":
                            if (currentUser != null) {
                                server.deleteAccount(currentUser);
                                currentUser = null;
                            }
                            break;

                        case "/m":
                            currentRoom = command[1];
                            break;

                        default:
                            throw new IllegalArgumentException("bad command");
                    }
                } catch (Exception e) {
                    System.err.println("> Bad Command");
                }
            } else {
                if (currentUser != null && currentRoom != null) {
                    server.sendMessage(currentRoom, currentUser, line);
                }
            }
        }
    }

    /**
     * @param host Java RMI registry hostname or null.  If null, defaults to
     * localhost, as per the LocateRegistry.getRegistry() behavior.
     * @return chat server as Protocol262 remote stub
     * @throws Exception 
     */
    static Protocol262 getServer(String host) throws Exception {
        Registry registry = LocateRegistry.getRegistry(host);
        Protocol262 stub = (Protocol262) registry.lookup("chat262");
        return stub;
    }

}