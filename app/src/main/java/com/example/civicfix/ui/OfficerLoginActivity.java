package com.example.civicfix.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.civicfix.databinding.ActivityOfficerLoginBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class OfficerLoginActivity extends AppCompatActivity {

    private ActivityOfficerLoginBinding b;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityOfficerLoginBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        setTitle("Officer login");
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        b.btnLogin.setOnClickListener(v -> login());
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        // An officer who logged in earlier stays logged in
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && !user.isAnonymous()) {
            setLoading(true);
            verifyAndOpen(user);
        }
    }

    private void login() {
        String email = b.etEmail.getText().toString().trim();
        String password = b.etPassword.getText().toString();
        if (email.isEmpty() || password.isEmpty()) {
            showError("Enter your email and password.");
            return;
        }

        setLoading(true);
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> verifyAndOpen(result.getUser()))
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showError("Login failed: " + e.getMessage());
                });
    }

    /** The account must also have a document in the "officers" collection. */
    private void verifyAndOpen(FirebaseUser user) {
        FirebaseFirestore.getInstance().collection("officers").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists() && doc.getString("department") != null) {
                        startActivity(new Intent(this, OfficerQueueActivity.class));
                        finish();
                    } else {
                        FirebaseAuth.getInstance().signOut();
                        setLoading(false);
                        showError("This account is not registered as a municipal officer.");
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showError("Could not verify the account: " + e.getMessage());
                });
    }

    private void setLoading(boolean loading) {
        b.btnLogin.setEnabled(!loading);
        b.btnLogin.setText(loading ? "Please wait..." : "Log in");
        if (loading) b.tvError.setVisibility(View.GONE);
    }

    private void showError(String message) {
        b.tvError.setText(message);
        b.tvError.setVisibility(View.VISIBLE);
    }
}