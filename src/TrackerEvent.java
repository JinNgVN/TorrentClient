public enum TrackerEvent {
    NONE(0,"none"),
    COMPLETED(1,"completed"),
    STARTED(2,"started"),
    STOPPED(3,"stopped");
    private final String strValue;
    private final int intValue;
    TrackerEvent(int intvalue, String strValue) {
        this.strValue = strValue;
        this.intValue = intvalue;
    }
    public String getStringValue() {
        return strValue;
    }
    public int getIntValue() {
        return intValue;
    }
}
