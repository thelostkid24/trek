package com.sahyatri.auth.entity;

import com.sahyatri.common.acquisition.HeardFrom;
import com.sahyatri.common.acquisition.Touch;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;

    private String fullName;

    private String email;

    private String phone;

    private String passwordHash;

    private String googleSubject;

    private String avatarKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    private Instant emailVerifiedAt;

    private Instant phoneVerifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(updatable = false)
    private SignupMethod signupMethod;

    /** First touch (§6.11): set once, when the account is created. */
    @Embedded
    @AttributeOverride(name = "seenAt", column = @Column(name = "first_seen_at"))
    private Touch firstTouch;

    @Enumerated(EnumType.STRING)
    private HeardFrom heardFrom;

    private String heardFromNote;

    /** Written by {@code UserRepository.markSeen} only, so it never bumps {@code updated_at}. */
    @Column(insertable = false, updatable = false)
    private Instant lastSeenAt;

    private Instant marketingEmailConsentAt;

    private Instant marketingWhatsappConsentAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected User() {
    }

    /** New self-registered account. Public sign-up is always a trekker. */
    public static User newTrekker() {
        User user = new User();
        user.id = UUID.randomUUID();
        user.role = Role.TREKKER;
        user.status = UserStatus.ACTIVE;
        user.createdAt = Instant.now();
        user.updatedAt = user.createdAt;
        return user;
    }

    /** Guest checkout: a trekker with a name and no sign-in identity. The booking holds their contact details. */
    public static User newGuest(String fullName) {
        User user = newTrekker();
        user.fullName = fullName;
        return user;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    /** No email, phone or Google yet. Verifying a phone or email makes this a normal account. */
    public boolean isGuest() {
        return email == null && phone == null && googleSubject == null;
    }

    public List<AuthMethod> authMethods() {
        List<AuthMethod> methods = new ArrayList<>();
        if (passwordHash != null) methods.add(AuthMethod.PASSWORD);
        if (phoneVerifiedAt != null) methods.add(AuthMethod.PHONE_OTP);
        if (googleSubject != null) methods.add(AuthMethod.GOOGLE);
        return methods;
    }

    public boolean isDisabled() {
        return status == UserStatus.DISABLED;
    }

    public UUID getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getGoogleSubject() {
        return googleSubject;
    }

    public void setGoogleSubject(String googleSubject) {
        this.googleSubject = googleSubject;
    }

    public String getAvatarKey() {
        return avatarKey;
    }

    public void setAvatarKey(String avatarKey) {
        this.avatarKey = avatarKey;
    }

    public Role getRole() {
        return role;
    }

    /** Role changes are admin actions (bootstrap, guide promotion); the next token refresh carries the new role. */
    public void setRole(Role role) {
        this.role = role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public void setEmailVerifiedAt(Instant emailVerifiedAt) {
        this.emailVerifiedAt = emailVerifiedAt;
    }

    public Instant getPhoneVerifiedAt() {
        return phoneVerifiedAt;
    }

    public void setPhoneVerifiedAt(Instant phoneVerifiedAt) {
        this.phoneVerifiedAt = phoneVerifiedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public SignupMethod getSignupMethod() {
        return signupMethod;
    }

    public Touch getFirstTouch() {
        return firstTouch;
    }

    public HeardFrom getHeardFrom() {
        return heardFrom;
    }

    public String getHeardFromNote() {
        return heardFromNote;
    }

    /** Where a new account came from. Only before the first save; the columns are never rewritten. */
    public void setAcquisition(SignupMethod method, Touch firstTouch, HeardFrom heardFrom, String heardFromNote) {
        this.signupMethod = method;
        this.firstTouch = firstTouch;
        this.heardFrom = heardFrom;
        this.heardFromNote = heardFromNote;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public Instant getMarketingEmailConsentAt() {
        return marketingEmailConsentAt;
    }

    public void setMarketingEmailConsentAt(Instant marketingEmailConsentAt) {
        this.marketingEmailConsentAt = marketingEmailConsentAt;
    }

    public Instant getMarketingWhatsappConsentAt() {
        return marketingWhatsappConsentAt;
    }

    public void setMarketingWhatsappConsentAt(Instant marketingWhatsappConsentAt) {
        this.marketingWhatsappConsentAt = marketingWhatsappConsentAt;
    }
}
