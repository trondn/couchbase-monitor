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

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import android.util.Log;

/**
 * Utility to download resources from the network
 * 
 * @author Trond Norbye
 */
public class WebSpider {
    /**
     * Download a given url and return it as a string
     * 
     * @param url
     *            the resource to download
     * @return The data as a string
     * @throws IOException
     *             If an error occurs (invalid id, missing resource etc)
     */
    public String download(String url) throws IOException {
        return download(new URL(url));
    }

    /**
     * Download a given url and return it as a string
     * 
     * @param url
     *            the resource to download
     * @return The data as a string
     * @throws IOException
     *             If an error occurs (invalid id, missing resource etc)
     */
    public String download(URL url) throws IOException {
        Log.d("org.couchbase.health.spider",
                "Download: " + url.toExternalForm());
        URLConnection connection = url.openConnection();

        if (getResponseCode(connection) != 200) {
            throw new FileNotFoundException(
                    Integer.toString(getResponseCode(connection)));
        }

        ByteArrayOutputStream out;
        if (connection.getContentLength() != -1) {
            out = new ByteArrayOutputStream(connection.getContentLength());
        } else {
            out = new ByteArrayOutputStream();
        }

        InputStream in = connection.getInputStream();
        byte[] array = new byte[8192];
        int nr;

        while ((nr = in.read(array)) > 0) {
            out.write(array, 0, nr);
        }
        return out.toString();
    }

    private int getResponseCode(URLConnection connection) {
        int ret = 200;
        String header = connection.getHeaderField(0);
        if (header != null) {
            String[] s = header.split(" ");
            if (s.length >= 2) {
                try {
                    ret = Integer.parseInt(s[1]);
                } catch (NumberFormatException ex) {
                    /* ignore */
                }
            }
        }

        return ret;
    }
}
