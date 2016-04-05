/**
 * Copyright (c) 2015-2016 Crisp AB
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR
 * ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH
 * THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package se.crisp.codekvast.testsupport.docker;

import lombok.Builder;
import lombok.NonNull;
import org.mariadb.jdbc.MariaDbDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * A Runnable that can be used as ready-checker when starting a {@link DockerContainer} containing a MariaDB image.
 *
 * @author olle.hallin@crisp.se
 */
@Builder
public class MariaDbContainerReadyChecker implements ContainerReadyChecker {

    @NonNull
    private final String host;
    private final int internalPort;
    @NonNull
    private final String database;
    private int timeoutSeconds;
    private final String username;
    private final String password;

    @Override
    public int getInternalPort() {
        return internalPort;
    }

    @Override
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    @Override
    public void check(int externalPort) throws ContainerNotReadyException {
        try {
            DataSource dataSource = new MariaDbDataSource(host, externalPort, database);
            try (Connection connection = dataSource.getConnection(username, password)) {
                if (!connection.isValid(timeoutSeconds)) {
                    throw new ContainerNotReadyException(this + " is not ready");
                }
            }
        } catch (SQLException e) {
            throw new ContainerNotReadyException(this + " is not ready", e);
        }
    }
}
