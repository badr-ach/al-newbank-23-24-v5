package groupB.newbankV5.paymentprocessor.entities;

import groupB.newbankV5.paymentprocessor.components.Integrator;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import javax.persistence.*;
import java.sql.Date;

@Entity
public class Application {
    @Id
    @GeneratedValue
    @Column(name = "Application_id", nullable = false)
    private Long id;
    private String name;
    private String email;
    private String url;
    private String description;

    @Column(unique = true)
    private String apiKey;

    @OneToOne(mappedBy = "application")
    private Merchant merchant;

    public Application() {
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public Application(String name, String email, String url, String description) {
        this.name = name;
        this.email = email;
        this.url = url;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Merchant getMerchant() {
        return merchant;
    }

    public void setMerchant(Merchant merchant) {
        this.merchant = merchant;
    }

    public String generateToken(){
        String token =  Jwts.builder()
                .setIssuer("NewBank")
                .setSubject("API Key")
                .setExpiration(Date.from(java.time.Instant.now().plusSeconds(3600)))
                .claim("id", this.id)
                .claim("name", this.name)
                .claim("email", this.email)
                .claim("url", this.url)
                .claim("description", this.description)
                .claim("dateOfIssue", System.currentTimeMillis())
                .signWith(SignatureAlgorithm.HS256, Integrator.SECRET_KEY)
                .compact();
        this.setApiKey(token);
        return token;
    }
}
