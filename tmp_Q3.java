import java.sql.*;

public class tmp_Q3 {
  public static void main(String[] a) throws Exception {
    Class.forName("com.mysql.cj.jdbc.Driver");
    try (Connection c = DriverManager.getConnection(
            "jdbc:mysql://127.0.0.1:3306/UAVPlatform", "root", "meng1234");
         Statement s = c.createStatement()) {
      ResultSet r = s.executeQuery("SELECT * FROM t_fh2_config LIMIT 1");
      ResultSetMetaData md = r.getMetaData();
      if (r.next()) {
        for (int i = 1; i <= md.getColumnCount(); i++) {
          System.out.println(md.getColumnName(i) + "=" + r.getObject(i));
        }
      } else {
        System.out.println("no fh2_config row");
      }
      r = s.executeQuery(
          "SELECT id,fh2_task_folder_id,status,error_message FROM t_reconstruct_job "
              + "WHERE mission_execution_id=13 ORDER BY id DESC LIMIT 3");
      while (r.next()) {
        System.out.println("job id=" + r.getLong(1)
            + " folder=" + r.getObject(2)
            + " st=" + r.getString(3)
            + " err=" + r.getString(4));
      }
    }
  }
}
