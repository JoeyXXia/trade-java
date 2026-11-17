package dev.commerceflow.trade.infrastructure;

import java.util.*;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.*;
import dev.commerceflow.trade.catalog.CatalogAvailability;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.IllegalTransactionStateException;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("database") @Testcontainers
class CatalogIT {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>(TestImages.locked("MYSQL_IMAGE","mysql"))
        .withDatabaseName("cf_catalog").withUsername("catalog_test").withPassword("container-only-test-password");
    @DynamicPropertySource static void db(DynamicPropertyRegistry p){
        if(!MYSQL.isRunning())MYSQL.start();
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword()).locations("classpath:db/migration").cleanDisabled(true).load().migrate();
        p.add("spring.datasource.url",MYSQL::getJdbcUrl);p.add("spring.datasource.username",MYSQL::getUsername);p.add("spring.datasource.password",MYSQL::getPassword);
        byte[] key=new byte[64];new java.security.SecureRandom().nextBytes(key);String secret=Base64.getEncoder().encodeToString(key);p.add("commerceflow.jwt-secret-base64",()->secret);
    }
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired CatalogAvailability availability;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    String unique(){return "u"+UUID.randomUUID().toString().replace("-","");}
    JsonNode user(String role)throws Exception{
        var body=Map.of("loginName",unique(),"password","fixture-password-123");
        assertThat(http.postForEntity("/api/auth/register",body,String.class).getStatusCode().value()).isEqualTo(201);
        var login=http.postForEntity("/api/auth/login",body,String.class);assertThat(login.getStatusCode().value()).isEqualTo(200);
        var u=json.readTree(login.getBody());jdbc.update("UPDATE app_user SET role=? WHERE id=?",role,u.path("user").path("id").asLong());return u;
    }
    ResponseEntity<String> call(JsonNode u,HttpMethod method,String path,Object body){
        var headers=new HttpHeaders();headers.setBearerAuth(u.path("accessToken").asText());headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(path,method,new HttpEntity<>(body,headers),String.class);
    }
    JsonNode ok(ResponseEntity<String> r,int status)throws Exception{assertThat(r.getStatusCode().value()).isEqualTo(status);return json.readTree(r.getBody());}
    JsonNode create(JsonNode a)throws Exception{return ok(call(a,HttpMethod.POST,"/api/admin/catalog/products",Map.of("title",unique(),"description","description")),201);}
    Map<String,Object> sku(JsonNode p,String price,boolean enabled){return Map.of("skuCode",unique(),"name","SKU","priceCents",price,"enabled",enabled,"version",p.path("version").asText());}
    JsonNode add(JsonNode a,JsonNode p,String price)throws Exception{return ok(call(a,HttpMethod.POST,"/api/admin/catalog/products/"+p.path("id").asText()+"/skus",sku(p,price,true)),201);}
    Map<String,Object> edit(JsonNode p,boolean published){return Map.of("title",p.path("title").asText(),"description",p.path("description").asText(),"published",published,"version",p.path("version").asText());}
    JsonNode publish(JsonNode a,JsonNode p,boolean on)throws Exception{return ok(call(a,HttpMethod.PUT,"/api/admin/catalog/products/"+p.path("id").asText(),edit(p,on)),200);}
    String productPath(JsonNode p){return "/api/catalog/products/"+p.path("id").asText();}
    String quotePath(JsonNode p){return "/api/catalog/skus/"+p.path("skus").get(0).path("id").asText()+"/quote";}

    @Test void draftThenPublishedDetailAndExactCentQuote()throws Exception{
        var a=user("ADMIN");var b=user("BUYER");var p=create(a);
        assertThat(call(b,HttpMethod.GET,productPath(p),null).getStatusCode().value()).isEqualTo(404);
        p=add(a,p,"9007199254740993");p=publish(a,p,true);
        var detail=ok(call(b,HttpMethod.GET,productPath(p),null),200);
        assertThat(detail.path("skus").get(0).path("priceCents").isTextual()).isTrue();
        assertThat(detail.path("skus").get(0).path("priceCents").asText()).isEqualTo("9007199254740993");
        var q=ok(call(b,HttpMethod.POST,quotePath(p),Map.of("quantity",2)),200);
        assertThat(q.path("totalCents").asText()).isEqualTo("18014398509481986");assertThat(q.path("currency").asText()).isEqualTo("CNY");
        assertThat(jdbc.queryForObject("SELECT created_by FROM catalog_product WHERE id=?",Long.class,p.path("id").asLong())).isEqualTo(a.path("user").path("id").asLong());
    }
    @Test void malformedMoneyNumericJsonAndInvalidQuantityCannotWriteOrQuote()throws Exception{
        var a=user("ADMIN");var p=create(a);String path="/api/admin/catalog/products/"+p.path("id").asText()+"/skus";
        for(String price:List.of("0","-1","1.2","1e2","01"," 1","9223372036854775808","9".repeat(1000)))
            assertThat(call(a,HttpMethod.POST,path,sku(p,price,true)).getStatusCode().value()).isEqualTo(400);
        var numeric=new HashMap<String,Object>(sku(p,"1",true));numeric.put("priceCents",100);
        assertThat(call(a,HttpMethod.POST,path,numeric).getStatusCode().value()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog_sku WHERE product_id=?",Integer.class,p.path("id").asLong())).isZero();
        p=add(a,p,Long.toString(Long.MAX_VALUE));p=publish(a,p,true);
        for(Object qty:List.of(0,-1,1000,1.5,"2"))assertThat(call(a,HttpMethod.POST,quotePath(p),Map.of("quantity",qty)).getStatusCode().value()).isEqualTo(400);
        assertThat(call(a,HttpMethod.POST,quotePath(p),Map.of("quantity",2)).getStatusCode().value()).isEqualTo(400);
        assertThat(call(a,HttpMethod.POST,quotePath(p),Map.of("quantity",1)).getStatusCode().value()).isEqualTo(200);
    }
    @Test void unpublishOrDisableSkuBlocksTheSharedTradeGuard()throws Exception{
        var a=user("ADMIN");var b=user("BUYER");var p=publish(a,add(a,create(a),"1234"),true);
        String quote=quotePath(p);p=publish(a,p,false);
        assertThat(call(b,HttpMethod.POST,quote,Map.of("quantity",1)).getStatusCode().value()).isEqualTo(409);
        var s=p.path("skus").get(0);
        var payload=Map.of("skuCode",s.path("skuCode").asText(),"name","disabled","priceCents","1234","enabled",false,"version",p.path("version").asText());
        p=ok(call(a,HttpMethod.PUT,"/api/admin/catalog/products/"+p.path("id").asText()+"/skus/"+s.path("id").asText(),payload),200);
        assertThat(call(b,HttpMethod.POST,quote,Map.of("quantity",1)).getStatusCode().value()).isEqualTo(409);
        assertThat(call(a,HttpMethod.PUT,"/api/admin/catalog/products/"+p.path("id").asText(),edit(p,true)).getStatusCode().value()).isEqualTo(409);
        assertThatThrownBy(()->availability.requireSellable(s.path("id").asLong(),1)).isInstanceOf(IllegalTransactionStateException.class);
    }
    @Test void rolesUnknownFieldsAndLastActiveSkuAreEnforced()throws Exception{
        var a=user("ADMIN");var p=publish(a,add(a,create(a),"9"),true);String path="/api/admin/catalog/products/"+p.path("id").asText();
        for(String role:List.of("BUYER","OPERATOR")){var u=user(role);assertThat(call(u,HttpMethod.PUT,path,edit(p,false)).getStatusCode().value()).isEqualTo(403);}
        assertThat(http.getForEntity("/api/catalog/products",String.class).getStatusCode().value()).isEqualTo(401);
        var extra=new HashMap<String,Object>(edit(p,false));extra.put("updatedBy",123);
        assertThat(call(a,HttpMethod.PUT,path,extra).getStatusCode().value()).isEqualTo(400);
        var s=p.path("skus").get(0);var disable=Map.of("skuCode",s.path("skuCode").asText(),"name","SKU","priceCents","9","enabled",false,"version",p.path("version").asText());
        assertThat(call(a,HttpMethod.PUT,path+"/skus/"+s.path("id").asText(),disable).getStatusCode().value()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT published FROM catalog_product WHERE id=?",Boolean.class,p.path("id").asLong())).isTrue();
    }
    @Test void duplicateSkuAndStaleConcurrentEditsLeaveConsistentState()throws Exception{
        var a=user("ADMIN");var p=add(a,create(a),"50");var other=create(a);var s=p.path("skus").get(0);
        var duplicate=Map.of("skuCode",s.path("skuCode").asText(),"name","duplicate","priceCents","51","enabled",true,"version",other.path("version").asText());
        assertThat(ok(call(a,HttpMethod.POST,"/api/admin/catalog/products/"+other.path("id").asText()+"/skus",duplicate),409).path("code").asText()).isEqualTo("SKU_CODE_UNAVAILABLE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog_sku WHERE product_id=?",Integer.class,other.path("id").asLong())).isZero();
        String path="/api/admin/catalog/products/"+p.path("id").asText();var payload=edit(p,true);var barrier=new CyclicBarrier(2);
        try(var pool=Executors.newFixedThreadPool(2)){
            var one=pool.submit(()->{barrier.await(10,TimeUnit.SECONDS);return call(a,HttpMethod.PUT,path,payload);});
            var two=pool.submit(()->{barrier.await(10,TimeUnit.SECONDS);return call(a,HttpMethod.PUT,path,payload);});
            assertThat(List.of(one.get(20,TimeUnit.SECONDS).getStatusCode().value(),two.get(20,TimeUnit.SECONDS).getStatusCode().value())).containsExactlyInAnyOrder(200,409);
        }
        assertThat(jdbc.queryForObject("SELECT version FROM catalog_product WHERE id=?",Long.class,p.path("id").asLong())).isEqualTo(2L);
    }
    @Test void tradeGuardReadsCurrentPriceAndPublicationEvenAfterAnEarlierRepeatableReadSnapshot()throws Exception{
        var admin=user("ADMIN");
        for(boolean takeOffShelf:List.of(false,true)){
            var product=publish(admin,add(admin,create(admin),"100"),true);
            long skuId=product.path("skus").get(0).path("id").asLong();
            var snapshotTaken=new CountDownLatch(1);var changed=new CountDownLatch(1);
            try(var pool=Executors.newSingleThreadExecutor()){
                var result=pool.submit(()->new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status->{
                    assertThat(jdbc.queryForObject("SELECT price_cents FROM catalog_sku WHERE id=?",Long.class,skuId)).isEqualTo(100L);
                    snapshotTaken.countDown();
                    try{if(!changed.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Edit did not finish");}
                    catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
                    return availability.requireSellable(skuId,1);
                }));
                assertThat(snapshotTaken.await(10,TimeUnit.SECONDS)).isTrue();
                try{
                    if(takeOffShelf)publish(admin,product,false);
                    else{
                        var s=product.path("skus").get(0);
                        var payload=Map.of("skuCode",s.path("skuCode").asText(),"name","new price","priceCents","200","enabled",true,"version",product.path("version").asText());
                        ok(call(admin,HttpMethod.PUT,"/api/admin/catalog/products/"+product.path("id").asText()+"/skus/"+skuId,payload),200);
                    }
                }finally{changed.countDown();}
                if(takeOffShelf)assertThatThrownBy(()->result.get(15,TimeUnit.SECONDS)).hasCauseInstanceOf(dev.commerceflow.trade.catalog.CatalogTypes.Conflict.class);
                else assertThat(result.get(15,TimeUnit.SECONDS).unitPriceCents()).isEqualTo("200");
            }
        }
    }
    @Test void whitelistedStablePagingParameterizedTitlesAndDatabaseConstraints()throws Exception{
        var a=user("ADMIN");var p=create(a);String injection="x'); DROP TABLE catalog_product; --";
        var payload=Map.of("title",injection,"description","<script>not executed</script>","published",false,"version",p.path("version").asText());
        var edited=ok(call(a,HttpMethod.PUT,"/api/admin/catalog/products/"+p.path("id").asText(),payload),200);
        assertThat(edited.path("title").asText()).isEqualTo(injection);
        assertThat(call(a,HttpMethod.GET,"/api/catalog/products?sort=id%20DESC;DROP%20TABLE%20catalog_product",null).getStatusCode().value()).isEqualTo(400);
        for(String query:List.of("page=0","size=51","page=10001","page=999999999999999999999"))
            assertThat(call(a,HttpMethod.GET,"/api/catalog/products?"+query,null).getStatusCode().value()).isEqualTo(400);
        var first=ok(call(a,HttpMethod.GET,"/api/admin/catalog/products?sort=oldest&size=1&page=1",null),200);
        var second=ok(call(a,HttpMethod.GET,"/api/admin/catalog/products?sort=oldest&size=1&page=2",null),200);
        assertThat(first.path("items").get(0).path("id").asLong()).isLessThan(second.path("items").get(0).path("id").asLong());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='catalog_product' AND index_name='ix_catalog_published_title'",Integer.class)).isEqualTo(3);
        assertThatThrownBy(()->jdbc.update("INSERT INTO catalog_sku(product_id,sku_code,name,price_cents,created_by,updated_by) VALUES (?,?,?,?,?,?)",p.path("id").asLong(),unique(),"invalid",0,a.path("user").path("id").asLong(),a.path("user").path("id").asLong())).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
}
