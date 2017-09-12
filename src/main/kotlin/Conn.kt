import java.sql.*
import org.apache.commons.dbutils.*
import org.apache.commons.dbutils.handlers.MapHandler
import org.apache.commons.dbutils.handlers.ScalarHandler

class KittenRest (val name: String, val cuteness: Int)


object Conn {
    @Throws(Exception::class)
    @JvmStatic fun main(a: Array<String>) {
        Class.forName("org.h2.Driver")
        val conn = DriverManager.getConnection("jdbc:h2:mem:test")

        val r = QueryRunner()
        val h = ScalarHandler<String>()
        val m = MapHandler()

        val rs = r.query(conn, "SELECT 'foo' as foo", m)

        println(rs["foo"])
        conn.close()
    }

}
