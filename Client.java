import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 
 * @author Aven Au z5208734
 *
 */

public class Client {
	private static int minPort = 1024;
	private static int maxPort = 65534;
	private static ServerSocket peerServer;
	private static InetAddress peerTalkerIp;
	private static int peerTalkerPort;
	private static String peerTalkerName;
	private static Socket peerTalkerSocket;
	private static PrintWriter outToPeer;
	
	
	public static void main(String[] args) throws Exception {
        if(args.length != 2){
            System.out.println("Usage: java Client localhost PortNo");
            System.exit(1);
        }
        
        InetAddress IPAddress = InetAddress.getByName(args[0]);
        int serverPort = Integer.parseInt(args[1]);
		Socket clientSocket = new Socket(IPAddress, serverPort);

		//Initialise		
    	BufferedReader inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
    	BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
    	DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream());
    	CommandInput CommandHandler = new CommandInput(inFromUser, outToServer);
    	//Starting the thread for input commands
    	Thread commandThread = new Thread(CommandHandler);
    	commandThread.start();
    	
    	//Constantly Checking for new messages from the server
        while (true) {
        	String serverMessage = null;
        	serverMessage = inFromServer.readLine();
        	
        	String code; 
        	String[] parsedMessage;
        	if (serverMessage == null) {
        		break;
        	} else {
        		parsedMessage = serverMessage.split(":");
        		code = parsedMessage[0];
        	}
        	
        	String message;
        	
        	try {
        		message = serverMessage.replaceFirst(code + ":", "");
        	} catch (ArrayIndexOutOfBoundsException a) {
        		message = "empty";
        	}    
        	
        	//Authentication
        	if (code.equals("Authentication")) {
        		//Inputting username and password
        		if (message.equals("Username:")) {
        			System.out.print("Username: ");
        		} else if (message.equals("Password:")) {
        			System.out.print("Password: ");
        		//When have too many tries
        		} else if (message.equals("Invalid Password. Your account has been blocked. Please try again later")) {
        			System.out.println(message);
        			inFromServer.close();
        			CommandHandler.stop();
        			outToServer.close();
        			clientSocket.close();
        			break;
        		//When have too many tries
        		} else if (message.equals("Your account is blocked due to multiple login failures. Please try again later")){
        			System.out.println(message);
        			inFromServer.close();
        			CommandHandler.stop();
        			outToServer.close();
        			clientSocket.close();
        			break;
        		} else {
        			System.out.println(message);
        		}
        	//This is a queue that the Authentication process is successful, which calls for all the peerServer infromation to be intialised
        	} else if (code.equals("Username")) {
        		//Start the peerTopeer server for the private messaging
        		PeerServer PeerHandler = new PeerServer();
            	Thread peerServerHandler = new Thread(PeerHandler);
            	peerServerHandler.start();
            	Timer delay = new Timer();
            	delay.schedule(new TimerTask() {
    				@Override
    				public void run() {
    	            	try {
							outToServer.writeBytes("sendip " + peerServer.getInetAddress().getHostAddress() + "\n");
							outToServer.flush();
	    	            	outToServer.writeBytes("sendport " + peerServer.getLocalPort() + "\n");
	    	            	outToServer.flush();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
    	            	
    	            	
    				}
    			}, 500);    
            //Queue to logout
        	} else if (code.equals("Logout")) {
        		System.out.println(message);
        		inFromServer.close();
        		inFromUser.close();
    			outToServer.close();
    			CommandHandler.stop();
    			peerServer.close();
    			clientSocket.close();
    			break;
    		//Queue to take in the peer clients ip
        	} else if (code.equals("SecretIP")) {
        		peerTalkerIp = InetAddress.getByName(message);
        	//Queue to take in the peer clients port
        	} else if (code.equals("SecretPort")) {
        		peerTalkerPort = Integer.parseInt(message);
        	//Queue to take in the peer clients port
        	} else if (code.equals("SecretName")) {
        		peerTalkerName = message;
        	//Queue to remove all the peer information
        	} else if (code.equals("RemoveSecret")) {
        		peerTalkerIp = null;
        		peerTalkerPort = 0;
        		peerTalkerName = null;
        	//This is to setup the connection between the peer server and you
        	} else if (code.equals("SetupSecret")) {
        		peerTalkerSocket = new Socket(peerTalkerIp, peerTalkerPort);
        		outToPeer = new PrintWriter(peerTalkerSocket.getOutputStream(), true);
        	//This is the print out all the message being sent on client
        	} else if (code.equals("Message")) {
        		System.out.println(message);
        	}

        }
		
	}
	//This is the thread for handling commands
	private static class CommandInput implements Runnable {
		private BufferedReader userInput;
		private DataOutputStream sendServer;
		private boolean isStop;
		
		public CommandInput(BufferedReader in, DataOutputStream serverOut) {
			this.userInput = in;
			this.sendServer = serverOut;
			this.isStop = false;
		}
		@Override
		public void run() {
			String input = null;
			while (!isStop) {
	        	try {
	        		if (userInput.ready()) {
						input = userInput.readLine();
			        	if (input != null) {
			        		if (input.split(" ")[0].equals("private")) {
			        			if (input.split(" ").length < 3) {
			        				System.out.println("Error. private usage: private <user> <message>!");
			        			} else if (input.split(" ")[1].equalsIgnoreCase(peerTalkerName)) {
				        			outToPeer.write(input + "\n");
				        			outToPeer.flush();
			        			} else {
			        				System.out.println("Error. Private messaging not enabled!");
			        			}
			        			
			        		} else {
				        		sendServer.writeBytes(input + "\n");
				        		sendServer.flush();
			        		}
			        		
			        	}
	        		}
				} catch (IOException e) {
				}
			}
			
		}
		
		public void stop() {
			this.isStop = true;
		}		
	}
	
	//This handles everything that accepts new peer connections
	private static class PeerServer implements Runnable {
		private boolean isStopped; 
		public PeerServer() {
			isStopped = false;
		}

		@Override
		public void run() {
			peerServer = null;
			PeerChat peerChat = null;
			int temp = minPort;
			while (temp <= maxPort) {
				if (portAvaliable(temp)) {					
					try {
						peerServer = new ServerSocket(temp);
						peerServer.setReuseAddress(true);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					break;
				}
				temp++;
			}
            
            // The main thread is just accepting new connections
			try {
	            while (!isStopped) {
	                Socket client = peerServer.accept();
	                peerChat = new PeerChat(client);
	 
	                // The background thread will handle each client separately
	                new Thread(peerChat).start();
	            }
	        } catch (IOException e) {
	           // e.printStackTrace();
	        } finally {
	            if (peerServer != null) {
	                try {
	                	peerServer.close();
	                } catch (IOException e) {
	                }
	            }
	            if (peerChat != null) {
	            	peerChat.stop();
	            }
	        }
			
		}
		
		//Checks if port is avaliable
		public boolean portAvaliable(int port) {
			if (port < minPort || port > maxPort) {
				return false;
			}
			ServerSocket sock = null;
			
			try {
				sock = new ServerSocket(port);
				sock.setReuseAddress(true);
				return true;
			} catch (IOException e) {
				
			} finally {
				if (sock != null) {
					try {
						sock.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						
					}
				}
			}
			return false;
		}
		
	}
	
	//Handles all the commands and messages that come in that has anything to do with peer messaging
	private static class PeerChat implements Runnable {
		private Socket peerClient;
		private boolean isStop;
		public PeerChat (Socket peerClient) {
			this.peerClient = peerClient;
			isStop = false;
		}

		@Override
		public void run() {
			BufferedReader in = null;
			String clientCommand = null;
			try {
				in = new BufferedReader(new InputStreamReader(peerClient.getInputStream()));
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			try {
				while (!isStop) {
					if (in.ready()) {
						clientCommand = in.readLine();
						if (clientCommand != null) {
							String command = null;
							int commandLength = clientCommand.split(" ").length;
							command = clientCommand.split(" ")[0];
							if (command.equalsIgnoreCase("private")) {
								if (commandLength < 3) {
									outToPeer.write("message private usage: private <user> <message>!\n");
									outToPeer.flush();
								} else {
									String message = clientCommand.replaceFirst("private " + peerTalkerName + " ", "");
									System.out.println(peerTalkerName + "(private): " + message);
								}
								
							} else if (command.equalsIgnoreCase("message")) {
								System.out.println(clientCommand.replaceFirst("message ", ""));
							}
						}
					}
				}
			} catch (IOException e) {
				
			}
			
		}
		public void stop() {
			isStop = true;
		}
		
	}
	
}
