package org.pixmob.freemobile.netstat.content;

import org.pixmob.freemobile.netstat.Event;
import org.pixmob.freemobile.netstat.MobileOperator;

/**
 * Store statistics.
 * @author Pixmob
 */
public class Statistics {
    public Event[] events = new Event[0];
    public long orange2GTime;
    public long orange3GTime;
    public long orangeTime;
    public long freeMobile3GTime;
    public long freeMobile4GTime;
    public long freeMobileTime;
    public int orange2GUsePercent;
    public int orange3GUsePercent;
    public int orangeUsePercent;
    public int freeMobile3GUsePercent;
    public int freeMobileFemtocellUsePercent;
    public int freeMobile4GUsePercent;
    public int freeMobileUsePercent;
    public MobileOperator mobileOperator;
    public String mobileOperatorCode;
    public long connectionTime;
    public long screenOnTime;
    public long wifiOnTime;
    public long femtocellTime;
    public int battery;

    @Override
    public String toString() {
        return "Statistics[events=" + events.length + "; orange=" + orangeUsePercent + "%; free="
                + freeMobileUsePercent + "%]";
    }

    public static void roundTwoPercentagesUpTo100(double[] percents) {
        roundTwoPercentagesUpTo100(percents, 100);
    }

    public static void roundTwoPercentagesUpTo100(double[] percents, int sum) {
        if (percents.length != 2)
            throw new IllegalArgumentException("There should be only two percentages in the array");

        final double[] integerParts = { (int) percents[0], (int) percents[1] };
        final double[] decimalParts = { percents[0] - integerParts[0], percents[1] - integerParts[1] };

        if (percents[0] == 0 && percents[1] == 0)
            return;

        if (integerParts[0] + integerParts[1] == sum) {
            percents[0] = integerParts[0];
            percents[1] = integerParts[1];
            return;
        }

        if (decimalParts[0] <= decimalParts[1]) {
            percents[0] = integerParts[0];
            percents[1] = integerParts[1] + 1;
            return;
        }

        percents[0] = integerParts[0] + 1;
        percents[1] = integerParts[1];
    }
}