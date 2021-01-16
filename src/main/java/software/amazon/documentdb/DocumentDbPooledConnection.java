/*
 * Copyright <2020> Amazon.com, final Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, final Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, final WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, final either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 *
 */

package software.amazon.documentdb;

import software.amazon.jdbc.PooledConnection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * DocumentDb implementation of PooledConnection.
 */
public class DocumentDbPooledConnection extends PooledConnection implements javax.sql.PooledConnection {

    /**
     * DocumentDbPooledConnection constructor, initializes super class.
     * @param connection Connection Object.
     */
    public DocumentDbPooledConnection(final java.sql.Connection connection) {
        super(connection);
    }

    @Override
    public java.sql.Connection getConnection() throws SQLException {
        return new DocumentDbConnection(new Properties());
    }
}