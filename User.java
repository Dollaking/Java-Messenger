import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

public class User {
	private String name;
	private Socket socket;
	private ArrayList<User> blockedUsers;
	private Queue<String> offlineMessage;
	private long loginTime = 0;
	private String peerAddress;
	private int peerPort;
	public User (String name, Socket socket) {
		this.name = name;
		this.socket = socket;
		this.offlineMessage = new LinkedList<String>();
		this.blockedUsers = new ArrayList<User> ();
	}
	public String getName() {
		return name;
	}
	public Socket getSocket() {
		return socket;
	}
	public void setSocket(Socket sock) {
		this.socket = sock;
	}
	public ArrayList<User> getBlockedUsers() {
		return blockedUsers;
	}
	
	public void addBlockedUsers(User u) {
		this.blockedUsers.add(u);
	}

	public void removeBlockedUsers(User u) {
		this.blockedUsers.remove(u);
	}
	
	public void removeBlockedUsers(String username) {
		for (User u : blockedUsers) {
			if (u.getName().equals(username)) {
				blockedUsers.remove(u);
				break;
			}
		}
	}
	
	public boolean isBlocked(User u) {
		for (User user : blockedUsers) {
			if (user.getName().equals(u.getName())) {
				return true;
			}
		}
		return false;
	}
	
	public boolean isBlocked(String username) {
		for (User user : blockedUsers) {
			if (user.getName().equals(username)) {
				return true;
			}
		}
		return false;
	}
	
	public void setLoginTime() {
		this.loginTime = System.currentTimeMillis();
	}
	
	public void clearLoginTime() {
		this.loginTime = 0;
	}
	
	public long getLoginTime() {
		return this.loginTime;
	}
	public Queue<String> getOfflineMessage() {
		return this.offlineMessage;
	}
	public void addOfflineMessage(String message) {
		this.offlineMessage.add(message);
	}
	
	public void sendOfflineMessage() {
		try {
			PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
			while (!offlineMessage.isEmpty()) {
				out.write(offlineMessage.remove() + "\n");
				out.flush();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void setPeerAddress(String ip) {
		this.peerAddress = ip;
	}
	
	public void setPeerPort(int port) {
		this.peerPort = port;
	}
	
	public String getPeerAddress() {
		return peerAddress;
	}
	
	public int getPeerPort() {
		return peerPort;
	}
	public void setName(String name) {
		this.name = name;
	}
	
	
	public void setBlockedUsers(ArrayList<User> blockedUsers) {
		this.blockedUsers = blockedUsers;
	}
	public void setOfflineMessage(Queue<String> offlineMessage) {
		this.offlineMessage = offlineMessage;
	}
	public void setLoginTime(long loginTime) {
		this.loginTime = loginTime;
	}
	
	
	
	
	
	
	
	
	
}
