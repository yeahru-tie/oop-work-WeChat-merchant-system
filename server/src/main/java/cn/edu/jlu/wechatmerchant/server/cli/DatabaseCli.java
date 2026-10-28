package cn.edu.jlu.wechatmerchant.server.cli;

import cn.edu.jlu.wechatmerchant.server.application.ProductCatalog;
import cn.edu.jlu.wechatmerchant.server.persistence.ProductDao;
import cn.edu.jlu.wechatmerchant.server.persistence.SqliteDatabase;
import com.google.gson.GsonBuilder;
import java.sql.SQLException;

public final class DatabaseCli {
    private DatabaseCli() {
    }

    public static void main(String[] args) {
        if (args.length != 1 || (!"init".equals(args[0]) && !"list".equals(args[0]))) {
            System.err.println("Usage: DatabaseCli init|list");
            System.exit(2);
            return;
        }
        try {
            SqliteDatabase database = new SqliteDatabase(SqliteDatabase.configuredPath());
            if ("init".equals(args[0])) {
                database.initialize();
                System.out.println("Database ready: " + database.file());
            }
            ProductCatalog catalog = new ProductCatalog(new ProductDao(database));
            System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(catalog.list(20, 0)));
        } catch (SQLException | IllegalArgumentException exception) {
            System.err.println("Database operation failed: " + exception.getMessage());
            System.exit(1);
        }
    }
}
