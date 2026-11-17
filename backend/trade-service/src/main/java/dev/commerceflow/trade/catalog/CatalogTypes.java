package dev.commerceflow.trade.catalog;

import java.io.IOException;
import java.util.List;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.*;

public final class CatalogTypes {
    private CatalogTypes() {}
    public static class Invalid extends RuntimeException {}
    public static class Missing extends RuntimeException {}
    public static class Conflict extends RuntimeException {
        public final String code;
        public Conflict(String code) { this.code = code; }
    }
    public static long number(String text, boolean positive) {
        if (text == null || !text.matches("0|[1-9][0-9]{0,18}")) throw new Invalid();
        try {
            long value = Long.parseLong(text);
            if (positive && value == 0) throw new Invalid();
            return value;
        } catch (NumberFormatException e) { throw new Invalid(); }
    }
    // Jackson's default scalar coercion must not silently accept numeric JSON money.
    public static class StringOnly extends JsonDeserializer<String> {
        @Override public String deserialize(JsonParser p, DeserializationContext c) throws IOException {
            if (!p.hasToken(JsonToken.VALUE_STRING)) throw JsonMappingException.from(p, "Expected string");
            return p.getText();
        }
    }
    public static class IntegerOnly extends JsonDeserializer<Integer> {
        @Override public Integer deserialize(JsonParser p, DeserializationContext c) throws IOException {
            if (!p.hasToken(JsonToken.VALUE_NUMBER_INT)) throw JsonMappingException.from(p, "Expected integer");
            return p.getIntValue();
        }
    }
    public record Create(@NotBlank @Size(max=128) String title, @NotNull @Size(max=2000) String description) {}
    public record Edit(@NotBlank @Size(max=128) String title, @NotNull @Size(max=2000) String description,
                       @NotNull Boolean published, @NotNull @JsonDeserialize(using=StringOnly.class) String version) {}
    public record SkuWrite(@NotBlank @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9_.-]{0,63}") String skuCode,
                           @NotBlank @Size(max=128) String name,
                           @NotNull @JsonDeserialize(using=StringOnly.class) String priceCents,
                           @NotNull Boolean enabled,
                           @NotNull @JsonDeserialize(using=StringOnly.class) String version) {}
    public record Quantity(@NotNull @Min(1) @Max(999) @JsonDeserialize(using=IntegerOnly.class) Integer quantity) {}
    public record Sku(String id, String skuCode, String name, String priceCents, boolean enabled) {}
    public record Product(String id, String title, String description, boolean published, String version, List<Sku> skus) {}
    public record Summary(String id, String title, boolean published, String version, String minPriceCents) {}
    public record Page(List<Summary> items, long total, int page, int size) {}
    public record Quote(String productId, String skuId, String name, String unitPriceCents,
                        int quantity, String totalCents, String currency, String productVersion) {}
}
