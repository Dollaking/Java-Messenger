import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 
 * @author Aven Au z5208734
 *
 */
public class Server {
	static int blockDuration;
	static int timeoutDuration;
	public static ArrayList<String> blockedUsers = new ArrayList<String> () ;
	public static ArrayList<User> onlineUsers = new ArrayList<User> ();
	public static ArrayList<User> allUsers = new ArrayList<User> ();
	public static ArrayList<History> logHistory = new ArrayList<History>();
	
	
    public static void main(String[] args) {

		if (args.length < 3){
			System.out.println("Wrong inputs: java Server <server_port> <block_time> <timeout_time>");
			return ;
		}
    	int serverPort = Integer.parseInt(args[0]);
    	blockDuration = Integer.parseInt(args[1]);
    	timeoutDuration = Integer.parseInt(args[2]);
        ServerSocket server = null;
        BufferedReader fileReader;
        
        //Populating allUsers list
		try {
			fileReader = new BufferedReader(new FileReader("credentials.txt"));
			String line;
			String[] words;
			while ((line = fileReader.readLine()) != null) {
				words = line.split(" ");
				for (String username : words) {
					allUsers.add(new User(username, new Socket()));
				}

			}
			fileReader.close();
		} catch (FileNotFoundException e1) {
			System.out.println("credentials.txt does not exist!");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}       
        
		//Start Server and accepting clients
        try {
            server = new ServerSocket(serverPort);
            server.setReuseAddress(true);
            
            while (true) {
            	System.out.println("Ready for Connection Requests");
                Socket client = server.accept();
                Authentication AuthoriseUser = new Authentication(client);
 
                //Creates thread for authorising user
                new Thread(AuthoriseUser).start();
            }
        } catch (IOException e) {
        } finally {
            if (server != null) {
                try {
                    server.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    
    /**
     * This class will be made into a thread in order to Authorise username and password and anything close to that
     * @author Aven Au z5208734
     *
     */
    private static class Authentication implements Runnable {
    	 
        private final Socket clientSocket;
        private String username;
        private String password;
        private int tries;
 
        /**
         * 
         * @param socket The socket of the client
         */
        public Authentication(Socket socket) {
            this.clientSocket = socket;
            this.tries = 0;
        }
        
 
        @Override
        public void run() {
            authorise();
        }

        /**
         * The function which checks if username and password are right
         */
		private void authorise() {
			PrintWriter out = null;
            BufferedReader in = null;
            try {
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                out.write("Authentication:Username:\n");
                out.flush();
                username = in.readLine();
                out.write("Authentication:Password:\n");
                out.flush();
                password = in.readLine();
                
                //The users have already been blocked from excessive attempts
                if (blockedUsers.contains(username)) {
                	out.write("Authentication:Your account is blocked due to multiple login failures. Please try again later\n");
                	out.flush();
                //If username and password checks out
                } else if (checkUserPass(username, password)) {
                	out.write("Username:" + username + "\n");
                	out.flush();
                	out.write("Message:Welcome to Aven's messaging app!\n");
                	out.flush();
                	User loginUser = null;
                	for (User u : allUsers) {
                		if (u.getName().equals(username)) {
                			u.setSocket(clientSocket);
                			u.setLoginTime();
                			loginUser = u;
                			break;
                		}
                	}
                	if (loginUser == null) {
                		System.out.println("You are not in credentials.txt!");
                	} else {              	
	                	onlineUsers.add(loginUser);
	                	loginBroadcast(loginUser);
	                	loginUser.sendOfflineMessage();
	                	LoggedIn loginSession = new LoggedIn(username, clientSocket, loginUser);
	                	new Thread(loginSession).start();
                	}
                } else {               	
                	this.tries++;
                	if (tries < 3) {
                		out.write("Authentication:Invalid Password. Please try again\n");
                		out.flush();
                		authorise();
                	} else {
                		blockedUsers.add(username);
                		out.write("Authentication:Invalid Password. Your account has been blocked. Please try again later\n");
                		out.flush();
                		TimerBlocked userTimer = new TimerBlocked(username);
                		new Thread(userTimer).start();
                	}
                	
                }                              
            } catch (IOException e) {
                e.printStackTrace();
            }
		}
    }
    
    /**
     * Broadcast to everyone on the server that someone logged in
     * @param user The user who is logging in
     */
    public static void loginBroadcast (User user) {
    	for (User u : onlineUsers) {
    		if (!u.getName().equals(user.getName())) {
    			try {
					PrintWriter output = new PrintWriter(u.getSocket().getOutputStream(), true);
					output.write("Message:" + user.getName() + " logged in\n");
					output.flush();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
    		}
    	}
    	
    }
    
    /**
     * This class is for the delay on how long the user is blocked if they have logged in an account too much
     * @author Aven Au z5208734
     *
     */
    private static class TimerBlocked implements Runnable {
    	private final String username;
    	public TimerBlocked(String user) {
    		this.username = user;
    	}

		@Override
		public void run() {
			try{
	            Thread.sleep(blockDuration * 1000);//in milliseconds
	        } catch (InterruptedException e){
	            System.out.println(e);
	        }
			blockedUsers.remove(username);
			
		}
    }
    
    /**
     * This is the session, the main section where the user are logged in
     * @author Aven Au z5208734
     *
     */
    private static class LoggedIn implements Runnable {
    	private String username;
    	private Socket client;
    	private User userObject;
    	/**
    	 * 
    	 * @param user The current username
    	 * @param clientSocket The current user's client socket
    	 * @param userObject The is the user object of the user
    	 */
    	public LoggedIn(String user, Socket clientSocket, User userObject) {
    		this.username = user;
    		this.client = clientSocket;
    		this.userObject = userObject;
    	}

		@Override
		public void run() {
			BufferedReader in = null;
			PrintWriter out = null;
			Timer timeout;
			String clientCommand;
			timeout = new Timer();
			try {
				in = new BufferedReader(new InputStreamReader(client.getInputStream()));
				out = new PrintWriter(client.getOutputStream(), true);
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			
			setTimer(out, timeout);
			
			try {
				while (true) {
					try {
						clientCommand = in.readLine();
					} catch (IOException e) {
						continue;
					}
					String command = null;
					int commandLength = clientCommand.split(" ").length;
					command = clientCommand.split(" ")[0];
					
					//This handles anything that handles message commands
					if (command.equalsIgnoreCase("message")) {
						messageFunction(out, clientCommand, command, commandLength, username);
						timerReset(out,timeout);
					//This handles anything that handles broadcast commands
					} else if (command.equalsIgnoreCase("broadcast")) {
						//If there are not enough arguments in the command
						if (commandLength < 2) {
							out.write("Message:broadcast usage: broadcast <message>\n");
							out.flush();
						//Arguments are right
						} else {
							boolean isBlocked = false;
							String broadMess = clientCommand.replaceFirst("broadcast ", "");
							//Going through all online users and then checking if they are blocked
							for (User u : onlineUsers) {
								if (u.isBlocked(username)) {
									isBlocked = true;
								} else {
									PrintWriter recOut = new PrintWriter(u.getSocket().getOutputStream(), true);
									recOut.write("Message:" + username + ": " + broadMess + "\n");
									recOut.flush();
								}
							}
							if (isBlocked) {
								out.write("Message:Your message could not be delievered to some recipients\n");
								out.flush();
							}
						}
						timerReset(out,timeout);
					//Handles everything that has something to do with whoelse
					} else if (command.equalsIgnoreCase("whoelse")) {
						for (User u : onlineUsers) {
							if (!u.getName().equals(username)) {
								out.write("Message:" + u.getName() + "\n");
								out.flush();
							}						
						}
						out.flush();
						timerReset(out,timeout);
					//Handles logout
					} else if (command.equalsIgnoreCase("logout")) {
						//Remove user object from the onlineUsers
						for (User u : onlineUsers) {
							if (u.getName().equals(username)) {
								onlineUsers.remove(u);
								logHistory.add(new History(u, System.currentTimeMillis()));
						    	for (User u1 : onlineUsers) {
						    		if (!u1.getName().equals(username)) {
						    			try {
											PrintWriter output = new PrintWriter(u1.getSocket().getOutputStream(), true);
											output.write("Message:" + username + " logged out\n");
											output.flush();
										} catch (IOException e) {
											// TODO Auto-generated catch block
											e.printStackTrace();
										}
						    		}
						    	}
								out.write("Logout:You have successfully logged out!\n");
								out.flush();
								out.close();
								in.close();
								break;
							}
							
						}
					//Handles everything that has something to do with whoelsesince
					} else if (command.equalsIgnoreCase("whoelsesince")) {
						//Wrong argument
						if (commandLength < 2) {
							out.write("Message:whoelsesince usage: whoelsesince <time>\n");
							out.flush();
						//All arguments are right
						} else {
							long currentTime = System.currentTimeMillis();
							int time = Integer.parseInt(clientCommand.split(" ")[1]) * 1000;
							ArrayList<User> temp = new ArrayList<User> ();
							for (User u : onlineUsers) {
								if (!u.getName().equals(username)) {
									out.write("Message:" + u.getName() + "\n");
									out.flush();
									temp.add(u);
								}
							}
							for (History h : logHistory) {
								if (!temp.contains(h.getUser()) && h.isWithin(currentTime, time)) {
									if (!h.getUser().getName().equals(username)) {
										out.write("Message:" + h.getUser().getName() + "\n");
										out.flush();
										temp.add(h.getUser());
									}
								}
							}
						}
						timerReset(out,timeout);
					//Deals with anything that deals with block
					} else if (command.equalsIgnoreCase("block")) {
						boolean isSent = false;
						if (commandLength != 2) {
							out.write("Message:" + "Block usage: block <user>" + "\n");
							out.flush();
						} else {
							String user = clientCommand.split(" ")[1];
							if (username.equals(user)) {
								out.write("Message:Error. Cannot block self\n");
								out.flush();
							} else if (!isSent) {
								for (User u : allUsers) {
									if (u.getName().equals(user)) {
										isSent = true;
										userObject.addBlockedUsers(u);
										break;
									}
								}
								if (!isSent) {
									out.write("Message:" + user + " is not a valid username!\n");
									out.flush();
								} else {
									out.write("Message:" + user + " is blocked!\n");
									out.flush();
								}
							}
						}
						timerReset(out,timeout);
					//Deals with any unblock commands
					} else if (command.equalsIgnoreCase("unblock")) {
						if (commandLength != 2) {
							out.write("Message:" + "unblock usage: unblock <user>" + "\n");
							out.flush();
						} else {
							String user = clientCommand.split(" ")[1];
							if (userObject.isBlocked(user)){
								userObject.removeBlockedUsers(user);
								out.write("Message:Error. " + user + " is unblocked!\n");
								out.flush();
							} else {
								out.write("Message:" + user + " is not blocked!\n");
								out.flush();
							}
						}
						timerReset(out,timeout);
					//This is a command that will make client save the peer ip server
					} else if (command.equalsIgnoreCase("sendip")) {
						String ip = clientCommand.split(" ")[1];   
						userObject.setPeerAddress(ip);
					//This will make client save the peer port
					} else if (command.equalsIgnoreCase("sendport")) {
						int port = Integer.parseInt(clientCommand.split(" ")[1]);   
						userObject.setPeerPort(port);
					//This will start a private convo p2p
					} else if (command.equalsIgnoreCase("startprivate")) {
						if (commandLength != 2) {
							out.write("Message:" + "startprivate usage: startprivate <user>" + "\n");
							out.flush();
						} else {
							String secretClient = clientCommand.split(" ")[1];
							User secretUser = null;
							for (User u : allUsers) {
								if (u.getName().equals(secretClient)) {
									secretUser = u;
									break;
								}
							}
							if (secretUser == null || secretUser.getName().equals(username)) {
								out.write("Message:" + "Error. Invalid user" + "\n");
								out.flush();
							} else {
								if (!onlineUsers.contains(secretUser)) {
									out.write("Message:" + "User is not online" + "\n");
									out.flush();
								} else {
									if (secretUser.isBlocked(userObject)) {
										out.write("Message:" + "You can private message someone who has blocked you!" + "\n");
										out.flush();
									} else {
										out.write("SecretIP:" + secretUser.getPeerAddress() + "\n");
										out.flush();
										out.write("SecretPort:" + secretUser.getPeerPort() + "\n");
										out.flush();
										out.write("SecretName:" + secretUser.getName() + "\n");
										out.flush();
										out.write("SetupSecret:\n");
										out.flush();
										PrintWriter other = new PrintWriter(secretUser.getSocket().getOutputStream(), true);
										other.write("SecretIP:" + userObject.getPeerAddress() + "\n");
										other.flush();
										other.write("SecretPort:" + userObject.getPeerPort() + "\n");
										other.flush();
										other.write("SecretName:" + userObject.getName() + "\n");
										other.flush();
										other.write("SetupSecret:\n");
										other.flush();
										
										out.write("Message:Start private messaging with " + secretUser.getName() + "\n");
										out.flush();
										
									}
								}
							}
						}
						timerReset(out,timeout);
					//This deal with anything with stopprivate
					} else if (command.equalsIgnoreCase("stopprivate")) {
						if (commandLength != 2) {
							out.write("Message:" + "stopprivate usage: stopprivate <user>" + "\n");
							out.flush();
						} else {
							out.write("RemoveSecret:\n");
							out.flush();
							out.write("Message:You have stopped the private section!" + "\n");
							out.flush();
						}
						timerReset(out,timeout);
					} else {
						out.write("Message:Error. Invalid command!\n");
						out.flush();
						timerReset(out,timeout);
					}
				}			
			} catch (IOException e) {
				e.printStackTrace();
			} catch (NullPointerException np) {
					
			}			
		
		}

		/**
		 * Function for messaging from client to client
		 * @param out PrintWriter to write to the server
		 * @param clientCommand Input that was sent in from clients
		 * @param command The client
		 * @param commandLength client length
		 * @param senderUser The username of the sender
		 * @throws IOException
		 */
		private void messageFunction(PrintWriter out, String clientCommand, String command, int commandLength, String senderUser)
				throws IOException {
			boolean isSent = false;
			if (commandLength < 3) {
				out.write("Message:Usage for message is 'message <user> <message>!\n");
				out.flush();
			} else {
				String user = clientCommand.split(" ")[1];
				String message = clientCommand.replaceFirst(command + " " + user + " ", "");
				for (User u : onlineUsers) {
					if (u.getName().equals(user)) {
						if (!u.isBlocked(userObject)) {
							PrintWriter recieverOut = new PrintWriter(u.getSocket().getOutputStream(), true);
							recieverOut.write("Message:" + senderUser + ": " + message + "\n");
							recieverOut.flush();
						} else {
							out.write("Message:Your message could not be delievered as the recipient has blocked you!\n");
							out.flush();
						}
						
						isSent = true;
						break;
					}
				}
				if (!isSent) {
					for (User u : allUsers) {
						if (u.getName().equals(user)) {
							u.addOfflineMessage("Message:" + senderUser + ": " + message + "\n");
							isSent = true;
							break;
						}
					}
					if (!isSent) {
						out.write("Message:Error. Invalid user\n");
						out.flush();
					}
				}
			}
		}
		
		//Start the time for the timeout
		public void setTimer(PrintWriter out, Timer timeout) {
			
			timeout.schedule(new TimerTask() {
				@Override
				public void run() {
					for (User u : onlineUsers) {
						if (u.getName().equals(username)) {
							onlineUsers.remove(u);
							break;
						}
					}
					out.write("Logout:You have timed out!\n");
					out.flush();
					out.close();
				}
			}, timeoutDuration * 1000);
		}
		
		//Reset the timeout timer
		public void timerReset(PrintWriter out, Timer timeout) {
			timeout.cancel();
			timeout = new Timer();
			timeout.schedule(new TimerTask() {
				@Override
				public void run() {
					for (User u : onlineUsers) {
						if (u.getName().equals(username)) {
							onlineUsers.remove(u);
							break;
						}
					}
					out.write("Logout:You have timed out!\n");
					out.flush();
					out.close();
				}
			}, timeoutDuration * 1000);
			
		}
    }
    
    // Checks if the password and username is right
    public static boolean checkUserPass(String username, String password) {
    	try {
			BufferedReader fileReader = new BufferedReader(new FileReader("credentials.txt"));
			String line;
			String[] words;
			while ((line = fileReader.readLine()) != null) {
				words = line.split(" ");
				if(words[0].equals(username) && words[1].equals(password)) {
					fileReader.close();
					return true;
				}
			}
			fileReader.close();
			return false;
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			System.out.println("Credentials.txt does not exist!");
			return false;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
    }
}
