# Spinning up the test certificate authority

1. Create database table in CA's database;
    ```sql
    CREATE TABLE UnidFnrMapping (

        unid VARCHAR(250) NOT NULL DEFAULT '',
        fnr  VARCHAR(250) NOT NULL DEFAULT '',
        rowProtection TEXT,
        rowVersion INTEGER NOT NULL,
        PRIMARY KEY (unid));
    ```

2. Register table as data source in CA using `jboss-client.sh`;
    ```shell
    data-source add --name=unidds --jta=false --driver-name="jdbc-driver.jar" --connection-url="jdbc:postgresql://auth-grant-manager-ca-db-1/ejbca" --jndi-name="java:/UnidDS" --use-ccm=true --driver-class="org.postgresql.Driver" --user-name="uniduser" --password="unidpass" --validate-on-match=true --background-validation=false --prepared-statements-cache-size=50 --share-prepared-statements=true --min-pool-size=5 --max-pool-size=150 --pool-prefill=true --transaction-isolation=TRANSACTION_READ_COMMITTED --check-valid-connection-sql="select 1"
    ```

3. Enable the Unid-Fnr request processor under __CA Functions__ > __Certificate Authorities__ > _'ManagementCA'_ > __Edit CA__ > __Other Data__ > __Request Processor__ > _Norwegian FNR to Unid Converter_
