package utils;

public class Badge {

	private String code;
	private boolean isAssigned;

	public Badge(String code) {
		this.code = code;
	}

	public String getCode() {
		return code;
	}

	public boolean isAssigned() {
		return isAssigned;
	}

	public void setAssigned(boolean isAssigned) {
		this.isAssigned = isAssigned;
	}

}
