package com.example;

import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultConfig;
import com.bettercloud.vault.response.LogicalResponse;

public class MyVault {
    public String getClientId() {

        System.getenv().forEach((key, value) -> System.out.println(key + " = " + value));


        String vaultToken = System.getenv("VAULT_TOKEN");
        System.out.println(vaultToken);
        try {
            VaultConfig config = new VaultConfig().address("http://127.0.0.1:8200").token(vaultToken).build();
            Vault vault = new Vault(config);
            LogicalResponse response = vault.logical().read("secret/data/fire-heureka");
            return response.getData().get("CLIENT_ID");
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "";
    }
}   