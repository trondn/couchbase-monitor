/*
 *     Copyright 2011 Couchbase, Inc.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package org.couchbase.health;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.util.Log;

/**
 * A small utility class used to retrieve the state of a Couchbase Cluster.
 * 
 * @author Trond Norbye
 */
public class PoolHealthReader {
    /**
     * A small web spider we may use to download files from the internet
     */
    private WebSpider spider;

    /**
     * The URL to read the bootstrap information from
     */
    private final URL bootstrapUrl;

    /**
     * The URL containing the pool information (found within the document
     * returned from the bootstrap URL)
     */
    private URL poolUrl;

    /**
     * The name of the pool we're going to monitor
     */
    private String poolName;

    /**
     * Create a new instance of the PoolHealthReader
     * 
     * @param host
     * @param port
     * @param poolName
     */
    public PoolHealthReader(String host, int port, String poolName) {
        this.poolName = poolName;
        spider = new WebSpider();
        URL u = null;
        try {
            u = new URL("http", host, port, "/pools");
        } catch (MalformedURLException e) {
            Log.wtf("poolhealthreader.malformed.url", e);
            e.printStackTrace();
        }
        bootstrapUrl = u;
    }

    public URL getBootstrapUrl() {
        return bootstrapUrl;
    }

    /**
     * Get the state from all of the nodes in the cluster
     * 
     * @return an array containing all of the servers in the cluster. This
     *         should be refactored to return a host object so that we could
     *         provide more information about the failing nodes..
     * @throws IOException
     *             if we fail to send/receive data on the network
     */
    public State[] getStates() throws IOException {

        if (poolUrl == null) {
            bootstrap();
        }

        String json = spider.download(poolUrl);
        try {
            JSONObject root = (JSONObject) (new JSONTokener(json)).nextValue();
            JSONArray nodes = root.getJSONArray("nodes");
            State[] ret = new State[nodes.length()];

            for (int ii = 0; ii < nodes.length(); ++ii) {
                JSONObject obj = nodes.optJSONObject(ii);
                String status = obj.getString("status");
                if (status.equalsIgnoreCase("healthy")) {
                    ret[ii] = State.GOOD;
                } else {
                    ret[ii] = State.BAD;
                }
            }
            return ret;
        } catch (JSONException e) {
            Log.wtf("Failed to decode JSON response", e);
            throw new IOException("Invalid data returned from server");
        }
    }

    /**
     * Download the bootstrap document and locate the URL where we can find
     * information about the desired pool.
     * 
     * @throws IOException
     *             if an error occurs while we're trying to send / receive data
     *             on the network.
     */
    private void bootstrap() throws IOException {
        Log.d("org.couchbase.health",
                "Download bootstrap URL: " + bootstrapUrl.toExternalForm());
        String json = spider.download(bootstrapUrl);

        try {
            JSONObject root = (JSONObject) (new JSONTokener(json)).nextValue();
            JSONArray pools = root.getJSONArray("pools");
            for (int ii = 0; ii < pools.length(); ++ii) {
                JSONObject obj = pools.optJSONObject(ii);
                if (poolName.equalsIgnoreCase(obj.getString("name"))) {
                    // So this is it!!
                    String uri = obj.getString("uri");
                    if (uri.startsWith("/")) {
                        poolUrl = new URL(bootstrapUrl.getProtocol(),
                                bootstrapUrl.getHost(), bootstrapUrl.getPort(),
                                uri);

                    } else {
                        poolUrl = new URL(uri);
                    }
                    break;
                }
            }
        } catch (JSONException e) {
            Log.wtf("Failed to decode JSON response", e);
            throw new IOException("Invalid response");
        }
        if (poolUrl == null) {
            throw new FileNotFoundException("pool not found");
        }
    }
}
