
public class History {
	private User user;
	private long loginTime;
	private long logoutTime;
	public History (User user, long timeMill) {
		this.user = user;
		this.logoutTime = timeMill;
		this.loginTime = user.getLoginTime();
	}
	public User getUser() {
		return user;
	}
	
	public boolean isWithin(long currentTime, long limit) {
		long limitTime = currentTime - limit;
		
		if (limitTime <= logoutTime && limitTime >= loginTime) {
			return true;
		}
		return false;
	}
	
	public long getLogoutTime() {
		return this.logoutTime;
	}
}
