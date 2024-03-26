package com.sr.RateLimiter.cache;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.time.*;
import java.util.Set;

public class InMemoryDB {
    private Map<String, Integer> ipmap;
    private Set<String> blockedIp;
    private String[] requests;
    private long[] timeStamp;
    private int index;
    private int windowSize;
    private int maxCount;
    private boolean cacheLock;
    private int lastBlock;
    public InMemoryDB(int requestSize, int maxCount) {
        ipmap = new HashMap<>();
        blockedIp = new HashSet<>();
        windowSize = requestSize;
        requests = new String[requestSize];
        timeStamp = new long[requestSize];
        index = -1;
        this.maxCount = maxCount;
        // to clean the request window
        releaseLock();
        new Thread(new rollOver()).start();
    }

    private synchronized void getLock(){
        try {
            while (cacheLock) Thread.sleep(10);
            cacheLock = true;
        } catch (Exception e){
            cacheLock = false;
        }
    }

    private synchronized void releaseLock(){
        cacheLock = false;
    }

    public boolean isValid(String ip) {
        try {
            getLock();
            if (!blockedIp.contains(ip) && (index + 1)< windowSize) {
                int count = ipmap.containsKey(ip) ? ipmap.get(ip) : 0;
                if ((count + 1)< maxCount) {
                    ++index;
                    ++count;
                    ipmap.put(ip, count);
                    requests[index] = ip;
                    timeStamp[index] = Instant.now().getEpochSecond();
                    releaseLock();
                    return true;
                } else {
                    blockedIp.add(ip);
                }
            }
            releaseLock();
            return false;
        } catch (Exception e){
            releaseLock();
        }
        return false;
    }

    private class rollOver implements Runnable {
        @Override
        public void run() {
            lastBlock = 0;
            while (true) {
                try {
                    getLock();
                    int curr;
                    boolean isProcessed = false;
                    long NOW = Instant.now().getEpochSecond();
                    for (curr = lastBlock; curr <= index; curr++) {
                        if (timeStamp[curr] < NOW) {
                            isProcessed = true;
                            String ip = requests[curr];
                            int count = ipmap.get(ip) - 1;
                            count = (count <= 0)?0:count;
                            ipmap.put(ip, count);
                        } else break;
                    }
                    if (isProcessed) {
                        if( (curr >index) || (index == windowSize - 1)) {
                            index = -1;
                            lastBlock = 0;
                        } else lastBlock = curr + 1;
                    }
                    releaseLock();
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    releaseLock();
                    e.printStackTrace();
                }
            }
        }
    }
}
