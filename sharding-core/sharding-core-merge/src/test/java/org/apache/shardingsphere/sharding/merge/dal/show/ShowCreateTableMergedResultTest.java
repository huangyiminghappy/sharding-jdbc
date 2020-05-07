/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.sharding.merge.dal.show;

import org.apache.shardingsphere.api.config.sharding.ShardingRuleConfiguration;
import org.apache.shardingsphere.api.config.sharding.TableRuleConfiguration;
import org.apache.shardingsphere.core.rule.ShardingRule;
import org.apache.shardingsphere.sql.parser.binder.metadata.table.TableMetaData;
import org.apache.shardingsphere.sql.parser.binder.metadata.schema.SchemaMetaData;
import org.apache.shardingsphere.sql.parser.binder.statement.SQLStatementContext;
import org.apache.shardingsphere.underlying.executor.sql.QueryResult;
import org.junit.Before;
import org.junit.Test;

import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class ShowCreateTableMergedResultTest {
    
    private ShardingRule shardingRule;
    
    private SchemaMetaData schemaMetaData;
    
    @Before
    public void setUp() {
        shardingRule = createShardingRule();
        schemaMetaData = createSchemaMetaData();
    }
    
    private ShardingRule createShardingRule() {
        TableRuleConfiguration tableRuleConfig = new TableRuleConfiguration("table", "ds.table_${0..2}");
        ShardingRuleConfiguration shardingRuleConfig = new ShardingRuleConfiguration();
        shardingRuleConfig.getTableRuleConfigs().add(tableRuleConfig);
        return new ShardingRule(shardingRuleConfig, Collections.singletonList("ds"));
    }
    
    private SchemaMetaData createSchemaMetaData() {
        Map<String, TableMetaData> tableMetaDataMap = new HashMap<>(1, 1);
        tableMetaDataMap.put("table", new TableMetaData(Collections.emptyList(), Collections.emptyList()));
        return new SchemaMetaData(tableMetaDataMap);
    }
    
    @Test
    public void assertNextForEmptyQueryResult() throws SQLException {
        ShowCreateTableMergedResult actual = new ShowCreateTableMergedResult(shardingRule, mock(SQLStatementContext.class), schemaMetaData, Collections.emptyList());
        assertFalse(actual.next());
    }
    
    @Test
    public void assertNextForTableRuleIsPresent() throws SQLException {
        ShowCreateTableMergedResult actual = new ShowCreateTableMergedResult(shardingRule, mock(SQLStatementContext.class), schemaMetaData, Collections.singletonList(createQueryResult()));
        assertTrue(actual.next());
    }
    
    private QueryResult createQueryResult() throws SQLException {
        QueryResult result = mock(QueryResult.class);
        when(result.getColumnCount()).thenReturn(2);
        when(result.next()).thenReturn(true, false);
        when(result.getValue(1, Object.class)).thenReturn("table_0");
        when(result.getValue(2, Object.class)).thenReturn("CREATE TABLE `t_order` (\n"
                + "  `id` int(11) NOT NULL AUTO_INCREMENT,\n"
                + "  `order_id` int(11) NOT NULL COMMENT,\n"
                + "  `user_id` int(11) NOT NULL COMMENT,\n"
                + "  `status` tinyint(4) NOT NULL DEFAULT '1',\n"
                + "  `created_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,\n"
                + "  PRIMARY KEY (`id`)\n"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin");
        return result;
    }
}
