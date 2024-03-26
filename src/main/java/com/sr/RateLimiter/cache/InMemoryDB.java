package com.sr.RateLimiter.cache;

import com.sr.RateLimiter.constant.Constant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.time.*;
import java.util.Set;

@Component
public class InMemoryDB {
    private Map<String, Integer> ipMap;
    private Set<String> blockedIp;
    private String[] requests;
    private long[] timeStamp;
    private int index;
    private int windowSize;
    private int maxCount;
    private boolean cacheLock;

    public InMemoryDB() {
        windowSize = Constant.MAX_REQUESTS_PER_WINDOW;
        maxCount = Constant.MAX_REQUESTS_PER_IP;
        ipMap = new HashMap<>();
        blockedIp = new HashSet<>();
        requests = new String[windowSize];
        timeStamp = new long[windowSize];
        index = -1;
        // to clean the request window
        releaseLock();
        new Thread(new rollOver()).start();
    }

    private synchronized void getLock() {
        try {
            while (cacheLock) Thread.sleep(Constant.THREAD_WAIT);
            cacheLock = true;
        } catch (Exception e) {
            cacheLock = false;
        }
    }

    private synchronized void releaseLock() {
        cacheLock = false;
    }

    public boolean isValid(String ip) {
        try {
            getLock();
            if (!blockedIp.contains(ip) && (index + 1) < windowSize) {
                int count = ipMap.containsKey(ip) ? ipMap.get(ip) : 0;
                if ((count + 1) < maxCount) {
                    ++index;
                    ++count;
                    ipMap.put(ip, count);
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
        } catch (Exception e) {
            releaseLock();
        }
        return false;
    }

    private class rollOver implements Runnable {
        @Override
        public void run() {
            try {
                boolean isProcessed;
                int currentBlock = 0;
                int lastBlock = 0;
                long NOW = 0L;
                while (true) {
                    getLock();
                    isProcessed = false;
                    NOW = Instant.now().getEpochSecond();
                    for (currentBlock = lastBlock; currentBlock <= index; currentBlock++) {
                        if (timeStamp[currentBlock] < NOW) {
                            isProcessed = true;
                            String ip = requests[currentBlock];
                            int count = ipMap.get(ip) - 1;
                            count = (count <= 0) ? 0 : count;
                            ipMap.put(ip, count);
                        } else break;
                    }
                    if (isProcessed) {
                        if ((currentBlock > index) || (index == windowSize - 1)) {
                            index = -1;
                            lastBlock = 0;
                        } else {
                            lastBlock = currentBlock + 1;
                        }
                    }
                    releaseLock();
                    Thread.sleep(Constant.IP_PROCESS_WAIT);
                }
            } catch (InterruptedException e) {
                releaseLock();
                e.printStackTrace();
            }
        }
    }
}
