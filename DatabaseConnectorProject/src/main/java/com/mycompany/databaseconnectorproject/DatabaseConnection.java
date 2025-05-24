/**
 *
 * @author Estudiante_MCA
 */

package com.mycompany.databaseconnectorproject;

import java.sql.Connection;
import java.sql.SQLException;

public interface DatabaseConnection {
    Connection getConnection() throws SQLException;
    void close();
}