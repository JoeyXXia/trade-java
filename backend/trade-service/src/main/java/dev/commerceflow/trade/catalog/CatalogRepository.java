package dev.commerceflow.trade.catalog;

import java.sql.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.*;
import org.springframework.stereotype.Repository;
import static dev.commerceflow.trade.catalog.CatalogTypes.*;

@Repository @Profile("database")
public class CatalogRepository {
    private final JdbcTemplate jdbc;
    public CatalogRepository(JdbcTemplate jdbc) {
        this.jdbc=jdbc;
        jdbc.queryForList("SELECT id,title,description,published,version,created_by,updated_by FROM catalog_product LIMIT 0");
        jdbc.queryForList("SELECT id,product_id,sku_code,name,price_cents,enabled FROM catalog_sku LIMIT 0");
    }
    record Row(long id, String title, String description, boolean published, long version) {}
    Row product(long id, boolean lock) {
        return jdbc.query("SELECT id,title,description,published,version FROM catalog_product WHERE id=?"+(lock?" FOR UPDATE":""),
            (r,n)->new Row(r.getLong(1),r.getString(2),r.getString(3),r.getBoolean(4),r.getLong(5)),id)
            .stream().findFirst().orElseThrow(Missing::new);
    }
    Product detail(Row row, boolean admin) {
        var skus=jdbc.query("SELECT id,sku_code,name,price_cents,enabled FROM catalog_sku WHERE product_id=?"+
            (admin?"":" AND enabled=TRUE")+" ORDER BY id",(r,n)->new Sku(Long.toString(r.getLong(1)),r.getString(2),r.getString(3),Long.toString(r.getLong(4)),r.getBoolean(5)),row.id());
        return new Product(Long.toString(row.id()),row.title(),row.description(),row.published(),Long.toString(row.version()),skus);
    }
    Page page(int page, int size, String sort, boolean admin) {
        if(page<1 || page>10000 || size<1 || size>50) throw new Invalid();
        String order=switch(sort) {case "newest"->"p.id DESC";case "oldest"->"p.id ASC";case "title"->"p.title ASC,p.id ASC";default->throw new Invalid();};
        String where=admin?"":" WHERE p.published=TRUE";
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM catalog_product p"+where,Long.class);
        var items=jdbc.query("SELECT p.id,p.title,p.published,p.version,(SELECT MIN(s.price_cents) FROM catalog_sku s WHERE s.product_id=p.id AND s.enabled=TRUE) AS min_price FROM catalog_product p"+where+
            " ORDER BY "+order+" LIMIT ? OFFSET ?",(r,n)->new Summary(Long.toString(r.getLong(1)),r.getString(2),r.getBoolean(3),Long.toString(r.getLong(4)),
                r.getObject(5)==null?null:Long.toString(r.getLong(5))),size,(page-1)*size);
        return new Page(items,total,page,size);
    }
    long create(Create input, long actor) {
        var key=new GeneratedKeyHolder();
        jdbc.update(c->{var p=c.prepareStatement("INSERT INTO catalog_product(title,description,created_by,updated_by) VALUES (?,?,?,?)",Statement.RETURN_GENERATED_KEYS);
            p.setString(1,input.title().trim());p.setString(2,input.description());p.setLong(3,actor);p.setLong(4,actor);return p;},key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }
    void version(long productId,long actor) {
        if(jdbc.update("UPDATE catalog_product SET version=version+1,updated_by=? WHERE id=? AND version<?",actor,productId,Long.MAX_VALUE)!=1)
            throw new Conflict("CATALOG_VERSION_EXHAUSTED");
    }
    void expected(Row row,String expected) {
        if(row.version()!=number(expected,false)) throw new Conflict("CATALOG_STALE_VERSION");
    }
}
