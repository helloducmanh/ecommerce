## Task 0: Project Setup & Configuration

**Files:**
- Create: `pom.xml`
- Create: `src/main/java/com/shopnow/ShopNowApplication.java`
- Create: `src/main/resources/application.yml`
- Create: `src/test/resources/application-test.yml`
- Create: `.gitignore`

**Interfaces:**
- Produces: Working Spring Boot project with PostgreSQL + Redis dependencies

- [ ] **Step 1: Create Maven pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.1</version>
        <relativePath/>
    </parent>

    <groupId>com.shopnow</groupId>
    <artifactId>shopnow</artifactId>
    <version>0.1.0-SNAPSHOT</version>

    <properties>
        <java.version>17</java.version>
        <testcontainers.version>1.19.3</testcontainers.version>
    </properties>

    <dependencies>
        <!-- Spring Boot Starters -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>

        <!-- Database -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <version>${testcontainers.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>${testcontainers.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Create main application class**

```java
// src/main/java/com/shopnow/ShopNowApplication.java
package com.shopnow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ShopNowApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShopNowApplication.class, args);
    }
}
```

- [ ] **Step 3: Create application.yml**

```yaml
# src/main/resources/application.yml
spring:
  application:
    name: shopnow
  
  datasource:
    url: jdbc:postgresql://localhost:5432/shopnow
    username: shopnow
    password: shopnow
    driver-class-name: org.postgresql.Driver
  
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
    show-sql: false
  
  data:
    redis:
      host: localhost
      port: 6379
  
  flyway:
    enabled: true
    locations: classpath:db/migration

server:
  port: 8080

logging:
  level:
    com.shopnow: DEBUG
    org.hibernate.SQL: DEBUG
```

- [ ] **Step 4: Create test application.yml**

```yaml
# src/test/resources/application-test.yml
spring:
  datasource:
    url: jdbc:tc:postgresql:15:///testdb
    username: test
    password: test
  
  jpa:
    hibernate:
      ddl-auto: create-drop
  
  data:
    redis:
      host: localhost
      port: 6379
```

- [ ] **Step 5: Create .gitignore**

```
# .gitignore
target/
!.mvn/wrapper/maven-wrapper.jar
!**/src/main/**/target/
!**/src/test/**/target/

### STS ###
.apt_generated
.classpath
.factorypath
.project
.settings
.springBeans
.sts4-cache

### IntelliJ IDEA ###
.idea
*.iws
*.iml
*.ipr

### NetBeans ###
/nbproject/private/
/nbbuild/
/dist/
/nbdist/
/.nb-gradle/
build/
!**/src/main/**/build/
!**/src/test/**/build/

### VS Code ###
.vscode/

### Logs ###
*.log
logs/

### OS ###
.DS_Store
Thumbs.db
```

- [ ] **Step 6: Verify project compiles**

Run: `mvn clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit setup**

```bash
git add pom.xml src/main/java/com/shopnow/ShopNowApplication.java src/main/resources/application.yml src/test/resources/application-test.yml .gitignore
git commit -m "chore: initialize Spring Boot project with PostgreSQL and Redis"
```

---

