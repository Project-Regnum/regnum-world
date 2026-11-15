package zooller.regnumworld.api;

public enum GrowthResult {
    /**
     * External controller handled the growth tick. Regnum will cancel the vanilla random tick.
     */
    HANDLED,

    /**
     * External controller did not handle the tick. Regnum will proceed with its own logic.
     */
    PASS,

    /**
     * Explicitly cancel the growth tick (prevent any growth).
     */
    CANCEL
}
