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

    public static void roundPercentagesUpTo100(double[] percents) {
        roundPercentagesUpToN(percents, 100);
    }

    public static void roundPercentagesUpToN(double[] percents, int sum) {

        final double[] integerParts = new double[percents.length];
        final double[] decimalParts = new double[percents.length];
        for (int i = 0; i < percents.length; i++) {
            integerParts[i] = (int) percents[i];
            decimalParts[i] = percents[i] - integerParts[i];
        }

        double integerSum = 0;
        for (double integer : integerParts) {
            integerSum += integer;
        }
        if (integerSum < sum - 1) // Check if we can on day reach the sum
            return;

        if (integerSum == sum) {
            System.arraycopy(integerParts, 0, percents, 0, integerParts.length);
            return;
        }

        boolean earlyExit = false;
        for (int i = 0; i < percents.length - 1; i++) {
            if (decimalParts[i] <= decimalParts[i + 1]) {
                percents[i] = integerParts[i];
                percents[i + 1] = integerParts[i + 1] + 1;
                earlyExit = true;
            }
        }

        if (!earlyExit) {
            percents[0] = integerParts[0] + 1;
            System.arraycopy(integerParts, 1, percents, 1, integerParts.length - 1);
        }
    }
}