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
}