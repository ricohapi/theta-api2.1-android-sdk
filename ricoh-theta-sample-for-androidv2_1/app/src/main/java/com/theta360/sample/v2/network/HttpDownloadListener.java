package com.theta360.sample.v2.network;

/**
 * HTTP communication download listener class
 */
public interface HttpDownloadListener {
    /**
     * Total byte count
     */
    void onTotalSize(long totalSize);
    /**
     * Received byte count
     */
    void onDataReceived(int size);
}
