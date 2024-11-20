public enum TrackerEvent {
    STARTED("started"), COMPLETED("completed"), STOPPED("stopped");
    private final String value;
    TrackerEvent(String value) {
        this.value = value;
    }
    public String getValue() {
        return value;
    }
}
