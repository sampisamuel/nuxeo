/*
 * (C) Copyright 2006-2014 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Nuxeo - initial API and implementation
 *
 * $Id$
 */

package org.nuxeo.ecm.directory.sql;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.redis.RedisFeature;
import org.nuxeo.ecm.directory.AbstractDirectory;
import org.nuxeo.ecm.directory.DirectoryCache;
import org.nuxeo.ecm.directory.DirectoryException;
import org.nuxeo.ecm.directory.Session;
import org.nuxeo.runtime.metrics.MetricsService;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;

public class TestCachedSQLDirectory extends TestSQLDirectory {

    protected final static String CACHE_CONTRIB = "sql-directory-cache-config.xml";

    protected final static String REDIS_CACHE_CONFIG = "sql-directory-redis-cache-config.xml";

    protected final static String ENTRY_CACHE_NAME = "sql-entry-cache";

    protected final static String ENTRY_CACHE_WITHOUT_REFERENCES_NAME = "sql-entry-cache-without-references";

    @Override
    public void setUp() throws Exception {
        super.setUp();

        deployBundle("org.nuxeo.ecm.core.cache");

        if (RedisFeature.setup(this)) {
                deployTestContrib("org.nuxeo.ecm.directory.sql.tests",
                        REDIS_CACHE_CONFIG);
        } else {
            deployTestContrib("org.nuxeo.ecm.directory.sql.tests",
                    CACHE_CONTRIB);
        }
        fireFrameworkStarted();
        AbstractDirectory dir = getSQLDirectory();

        DirectoryCache cache = dir.getCache();
        cache.setEntryCacheName(ENTRY_CACHE_NAME);
        cache.setEntryCacheWithoutReferencesName(ENTRY_CACHE_WITHOUT_REFERENCES_NAME);

    }

    @Test
    public void testGetFromCache() throws DirectoryException, Exception {
        Session sqlSession = getSQLDirectory().getSession();
        MetricRegistry metrics = SharedMetricRegistries.getOrCreate(MetricsService.class.getName());
        Counter hitsCounter = metrics.counter(
                MetricRegistry.name("nuxeo", "directories", "userDirectory", "cache", "hits"));
        Counter negativeHitsCounter = metrics.counter(
                MetricRegistry.name("nuxeo", "directories", "userDirectory", "cache", "neghits"));
        Counter missesCounter = metrics.counter(
                MetricRegistry.name("nuxeo", "directories", "userDirectory", "cache", "misses"));
        long baseHitsCount = hitsCounter.getCount();
        long baseNegativeHitsCount = negativeHitsCounter.getCount();
        long baseMissesCount = missesCounter.getCount();

        // First call will update cache
        DocumentModel entry = sqlSession.getEntry("user_1");
        assertNotNull(entry);
        assertEquals(baseHitsCount, hitsCounter.getCount());
        assertEquals(baseNegativeHitsCount, negativeHitsCounter.getCount());
        assertEquals(baseMissesCount + 1, missesCounter.getCount());

        // Second call will use the cache
        entry = sqlSession.getEntry("user_1");
        assertNotNull(entry);
        assertEquals(baseHitsCount + 1, hitsCounter.getCount());
        assertEquals(baseNegativeHitsCount, negativeHitsCounter.getCount());
        assertEquals(baseMissesCount + 1, missesCounter.getCount());

        // Again
        entry = sqlSession.getEntry("user_1");
        assertNotNull(entry);
        assertEquals(baseHitsCount + 2, hitsCounter.getCount());
        assertEquals(baseNegativeHitsCount, negativeHitsCounter.getCount());
        assertEquals(baseMissesCount + 1, missesCounter.getCount());
    }

    @Test
    public void testNegativeCaching() throws Exception {
        DirectoryCache cache = getSQLDirectory().getCache();
        cache.setNegativeCaching(Boolean.TRUE);
        try {
            doTestNegativeCaching();
        } finally {
            cache.setNegativeCaching(null);
        }
    }

    protected void doTestNegativeCaching() throws Exception {
        Session sqlSession = getSQLDirectory().getSession();
        MetricRegistry metrics = SharedMetricRegistries.getOrCreate(MetricsService.class.getName());
        Counter hitsCounter = metrics.counter(
                MetricRegistry.name("nuxeo", "directories", "userDirectory", "cache", "hits"));
        Counter negativeHitsCounter = metrics.counter(
                MetricRegistry.name("nuxeo", "directories", "userDirectory", "cache", "neghits"));
        Counter missesCounter = metrics.counter(
                MetricRegistry.name("nuxeo", "directories", "userDirectory", "cache", "misses"));
        long baseHitsCount = hitsCounter.getCount();
        long baseNegativeHitsCount = negativeHitsCounter.getCount();
        long baseMissesCount = missesCounter.getCount();

        // First call will update cache
        DocumentModel entry = sqlSession.getEntry("NO_SUCH_USER");
        assertNull(entry);
        assertEquals(baseHitsCount, hitsCounter.getCount());
        assertEquals(baseNegativeHitsCount, negativeHitsCounter.getCount());
        assertEquals(baseMissesCount + 1, missesCounter.getCount());

        // Second call will use the negative cache
        entry = sqlSession.getEntry("NO_SUCH_USER");
        assertNull(entry);
        assertEquals(baseHitsCount, hitsCounter.getCount());
        assertEquals(baseNegativeHitsCount + 1, negativeHitsCounter.getCount());
        assertEquals(baseMissesCount + 1, missesCounter.getCount());

        // Again
        entry = sqlSession.getEntry("NO_SUCH_USER");
        assertNull(entry);
        assertEquals(baseHitsCount, hitsCounter.getCount());
        assertEquals(baseNegativeHitsCount + 2, negativeHitsCounter.getCount());
        assertEquals(baseMissesCount + 1, missesCounter.getCount());
    }
}