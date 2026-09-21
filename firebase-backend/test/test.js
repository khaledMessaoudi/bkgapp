const assert = require('assert');
const firebase = require('@firebase/testing');

const MY_PROJECT_ID = "superwall-c01dc";
const myId = "user_abc";
const theirId = "user_xyz";
const myAuth = { uid: myId, email: "testtest@testing1.com" };
const theirAuth = { uid: theirId, email: "testtest@testing2.com" };

function getFirestore(auth) {
    return firebase.initializeTestApp({ projectId: MY_PROJECT_ID, auth: auth }).firestore();
}

function getAdminFirestore() {
    return firebase.initializeAdminApp({ projectId: MY_PROJECT_ID }).firestore();
}

beforeEach(async () => {
    await firebase.clearFirestoreData({ projectId: MY_PROJECT_ID });
});

after(async () => {
    await firebase.clearFirestoreData({ projectId: MY_PROJECT_ID });
});

describe("Content Config Security Rules", () => {

    // bkgapp: the content API encryption key lives in Firestore instead of Cloud Storage,
    // which needs the paid Blaze plan. Same guarantee as upstream's storage.rules gave:
    // readable by any signed-in user, never writable from a client.
    const keyDoc = "content_config/encryption";

    async function seedKey() {
        const admin = getAdminFirestore();
        await admin.doc(keyDoc).set({ key: "obfuscated-key-value" });
    }

    it("lets a signed-in user read the content key", async () => {
        await seedKey();
        const db = getFirestore(myAuth);
        await firebase.assertSucceeds(db.doc(keyDoc).get());
    });

    it("denies an unauthenticated read of the content key", async () => {
        await seedKey();
        const db = getFirestore(null);
        await firebase.assertFails(db.doc(keyDoc).get());
    });

    it("denies a client write of the content key", async () => {
        await seedKey();
        const db = getFirestore(myAuth);
        await firebase.assertFails(db.doc(keyDoc).set({ key: "attacker" }));
    });
});

describe("User Profile Security Rules", () => {

    // Helper function to initialize user document
    async function initializeUserDoc(userId, data = {}) {
        const admin = getAdminFirestore();
        const userDoc = admin.collection('users').doc(userId);
        await userDoc.set({
            userId: userId,
            epochCreated: 1234567890,
            epochLastUpdated: 1234567890,
            epochLastSeen: 1234567890,
            isAnonymous: false,
            loginType: "firebase/google",
            ...data
        });
    }

    // Authentication and Access Control Tests
    it("Unauthenticated users cannot read any user profiles", async () => {
        const db = getFirestore(null);
        const testDoc = db.collection('users').doc(myId);
        await firebase.assertFails(testDoc.get());
    });

    it("Unauthenticated users cannot write to any user profiles", async () => {
        const db = getFirestore(null);
        const testDoc = db.collection('users').doc(myId);
        await firebase.assertFails(testDoc.set({}));
    });

    it("Users can read their own profile", async () => {
        // First, set up the document.
        await initializeUserDoc(myId);
        const db = getFirestore(myAuth);
        const testDoc = db.collection('users').doc(myId);
        await firebase.assertSucceeds(testDoc.get());
    });

    it("Users cannot read other users' profiles", async () => {
        // First, set up the document.
        await initializeUserDoc(theirId);
        const db = getFirestore(myAuth);
        const testDoc = db.collection('users').doc(theirId);
        await firebase.assertFails(testDoc.get());
    });

    // Creating a User Profile Tests
    it("Users can create their own profile with valid data", async () => {
        const db = getFirestore(myAuth);
        const testDoc = db.collection('users').doc(myId);
        const validData = {
            userId: myId,
            epochCreated: 1234567890,
            epochLastUpdated: 1234567890,
            epochLastSeen: 1234567890,
            isAnonymous: false,
            loginType: "firebase/google"
        };
        await firebase.assertSucceeds(testDoc.set(validData));
    });

    // Creating a User Profile Tests
    it("Users can create their own profile with valid data", async () => {
        const db = getFirestore(myAuth);
        const testDoc = db.collection('users').doc(myId);
        const validData = {
            userId: myId,
            epochCreated: 1234567890,
            epochLastUpdated: 1234567890,
            epochLastSeen: 1234567890,
            isAnonymous: false,
            loginType: "firebase/google"
        };
        await firebase.assertSucceeds(testDoc.set(validData));
    });

    it("Users cannot create their own profile without required fields", async () => {
        const db = getFirestore(myAuth);
        const testDoc = db.collection('users').doc(myId);
        const invalidData = {
            userId: myId,
            epochCreated: 1234567890,
            // Missing epochLastUpdated
            epochLastSeen: 1234567890,
            isAnonymous: false,
            loginType: "firebase/google"
        };
        await firebase.assertFails(testDoc.set(invalidData));
    });

    it("Users cannot create their own profile with invalid loginType", async () => {
        const db = getFirestore(myAuth);
        const testDoc = db.collection('users').doc(myId);
        const invalidData = {
            userId: myId,
            epochCreated: 1234567890,
            epochLastUpdated: 1234567890,
            epochLastSeen: 1234567890,
            isAnonymous: false,
            loginType: "invalid/loginType"
        };
        await firebase.assertFails(testDoc.set(invalidData));
    });

    it("Users cannot create their own profile with extra fields", async () => {
        const db = getFirestore(myAuth);
        const testDoc = db.collection('users').doc(myId);
        const invalidData = {
            userId: myId,
            epochCreated: 1234567890,
            epochLastUpdated: 1234567890,
            epochLastSeen: 1234567890,
            isAnonymous: false,
            loginType: "firebase/google",
            extraField: "not allowed"
        };
        await firebase.assertFails(testDoc.set(invalidData));
    });

    it("Users cannot create profile with 'userId' not matching document ID", async () => {
        const db = getFirestore(myAuth);
        const testDoc = db.collection('users').doc(myId);
        const invalidData = {
            userId: "someOtherId",
            epochCreated: 1234567890,
            epochLastUpdated: 1234567890,
            epochLastSeen: 1234567890,
            isAnonymous: false,
            loginType: "firebase/google"
        };
        await firebase.assertFails(testDoc.set(invalidData));
    });

    // Updating a User Profile Tests
    describe("Updating User Profiles", () => {
        beforeEach(async () => {
            await initializeUserDoc(myId);
        });
        
        it("Users cannot update fixed fields 'userId', 'epochCreated'", async () => {
            const db = getFirestore(myAuth);
            const testDoc = db.collection('users').doc(myId);
            await firebase.assertFails(testDoc.update({ userId: "newUserId" }));
            await firebase.assertFails(testDoc.update({ epochCreated: 9999999999 }));
        });

        it("Users can update 'epochLastUpdated', 'epochLastSeen'", async () => {
            const db = getFirestore(myAuth);
            const testDoc = db.collection('users').doc(myId);
            await firebase.assertSucceeds(testDoc.update({ epochLastUpdated: 1234567891, epochLastSeen: 1234567891 }));
        });

        it("Users can update 'favoriteIds' with a valid list", async () => {
            const db = getFirestore(myAuth);
            const testDoc = db.collection('users').doc(myId);
            await firebase.assertSucceeds(testDoc.update({ favoriteIds: ["item1", "item2"] }));
        });

        it("Users cannot update 'favoriteIds' with invalid data type", async () => {
            const db = getFirestore(myAuth);
            const testDoc = db.collection('users').doc(myId);
            await firebase.assertFails(testDoc.update({ favoriteIds: "not a list" }));
        });

        it("Users can update 'currentWallpaperIds' with a valid map", async () => {
            const db = getFirestore(myAuth);
            const testDoc = db.collection('users').doc(myId);
            const validMap = { "wallpaper1": "id1", "wallpaper2": "id2" };
            await firebase.assertSucceeds(testDoc.update({ currentWallpaperIds: validMap }));
        });

        it("Users cannot update 'currentWallpaperIds' with invalid data type", async () => {
            const db = getFirestore(myAuth);
            const testDoc = db.collection('users').doc(myId);
            await firebase.assertFails(testDoc.update({ currentWallpaperIds: "not a map" }));
        });

        it("Users can update 'email' with a valid email address", async () => {
            const db = getFirestore(myAuth);
            const testDoc = db.collection('users').doc(myId);
            await firebase.assertSucceeds(testDoc.update({ email: "user@example.com" }));
        });

        it("Users cannot update 'email' with an invalid email address", async () => {
            const db = getFirestore(myAuth);
            const testDoc = db.collection('users').doc(myId);
            await firebase.assertFails(testDoc.update({ email: "invalidemail" }));
        });

        it("Users can update 'newsletter' with a boolean value", async () => {
            const db = getFirestore(myAuth);
            const testDoc = db.collection('users').doc(myId);
            await firebase.assertSucceeds(testDoc.update({ newsletter: true }));
            await firebase.assertSucceeds(testDoc.update({ newsletter: false }));
        });

        it("Users cannot update 'newsletter' with an invalid data type", async () => {
            const db = getFirestore(myAuth);
            const testDoc = db.collection('users').doc(myId);
            await firebase.assertFails(testDoc.update({ newsletter: "yes" }));
        });

        it("Users cannot change 'isAnonymous' from false to true", async () => {
            const db = getFirestore(myAuth);
            const testDoc = db.collection('users').doc(myId);
            await firebase.assertFails(testDoc.update({ isAnonymous: true }));
        });

        it("Users can change 'isAnonymous' from true to false when upgrading account", async () => {
            // Set initial 'isAnonymous' to true
            const admin = getAdminFirestore();
            await admin.collection('users').doc(myId).set({
                userId: myId,
                epochCreated: 1234567890,
                epochLastUpdated: 1234567890,
                epochLastSeen: 1234567890,
                isAnonymous: true,
                loginType: "firebase/anonymous"
            });
            const db = getFirestore(myAuth);
            const testDoc = db.collection('users').doc(myId);
            await firebase.assertSucceeds(testDoc.update({ isAnonymous: false }));
        });

        it("Users can update 'loginType' from 'firebase/anonymous' to 'firebase/google'", async () => {
            // First, set up the profile with 'firebase/anonymous'
            const admin = getAdminFirestore();
            await admin.collection('users').doc(myId).set({
                userId: myId,
                epochCreated: 1234567890,
                epochLastUpdated: 1234567890,
                epochLastSeen: 1234567890,
                isAnonymous: true,
                loginType: "firebase/anonymous"
            });
            const db = getFirestore(myAuth);
            const testDoc = db.collection('users').doc(myId);
            await firebase.assertSucceeds(testDoc.update({ loginType: "firebase/google", isAnonymous: false }));
        });

        it("Users cannot change 'loginType' from 'firebase/google' to 'firebase/anonymous'", async () => {
            const db = getFirestore(myAuth);
            const testDoc = db.collection('users').doc(myId);
            await firebase.assertFails(testDoc.update({ loginType: "firebase/anonymous" }));
        });

        it("Users cannot update fields not in the allowed list", async () => {
            const db = getFirestore(myAuth);
            const testDoc = db.collection('users').doc(myId);
            await firebase.assertFails(testDoc.update({ unallowedField: "some value" }));
        });

        it("Users can set PII fields to null", async () => {
            const piiFields = ['email', 'deviceInfo', 'currency', 'newsletter', 'flags', 'receiveNotifications'];
            const db = getFirestore(myAuth);
            const testDoc = db.collection('users').doc(myId);
            let updateData = {};
            piiFields.forEach(field => updateData[field] = null);
            await firebase.assertSucceeds(testDoc.update(updateData));
        });

        it("Users can set 'accountDeleted' to true", async () => {
            const db = getFirestore(myAuth);
            const testDoc = db.collection('users').doc(myId);
            await firebase.assertSucceeds(testDoc.update({ accountDeleted: true }));
        });

        it("Users can update 'wallpaperDownloadEvents' with a valid list", async () => {
            const db = getFirestore(myAuth);
            const testDoc = db.collection('users').doc(myId);
            await firebase.assertSucceeds(testDoc.update({ wallpaperDownloadEvents: ["event1", "event2"] }));
        });

        it("Users cannot update 'wallpaperDownloadEvents' with invalid data type", async () => {
            const db = getFirestore(myAuth);
            const testDoc = db.collection('users').doc(myId);
            await firebase.assertFails(testDoc.update({ wallpaperDownloadEvents: "not a list" }));
        });

        it("Users can update 'receiveNotifications' with a boolean value", async () => {
            const db = getFirestore(myAuth);
            const testDoc = db.collection('users').doc(myId);
            await firebase.assertSucceeds(testDoc.update({ receiveNotifications: true }));
            await firebase.assertSucceeds(testDoc.update({ receiveNotifications: false }));
        });

        it("Users cannot update 'receiveNotifications' with an invalid data type", async () => {
            const db = getFirestore(myAuth);
            const testDoc = db.collection('users').doc(myId);
            await firebase.assertFails(testDoc.update({ receiveNotifications: "yes" }));
        });

        // Additional Field Validations with Document Initialization
        it("Users can update 'purchaseRecords' with a valid list", async () => {
            const db = getFirestore(myAuth);
            const testDoc = db.collection('users').doc(myId);
            await firebase.assertSucceeds(testDoc.update({ purchaseRecords: ["purchase1", "purchase2"] }));
        });
    
        it("Users cannot update 'purchaseRecords' with invalid data type", async () => {
            const db = getFirestore(myAuth);
            const testDoc = db.collection('users').doc(myId);
            await firebase.assertFails(testDoc.update({ purchaseRecords: "not a list" }));
        });
    
        it("Users can update 'flags' with a valid list", async () => {
            const db = getFirestore(myAuth);
            const testDoc = db.collection('users').doc(myId);
            await firebase.assertSucceeds(testDoc.update({ flags: ["flag1", "flag2"] }));
        });
    
        it("Users cannot update 'flags' with invalid data type", async () => {
            const db = getFirestore(myAuth);
            const testDoc = db.collection('users').doc(myId);
            await firebase.assertFails(testDoc.update({ flags: "not a list" }));
        });
    
        it("Users can update 'currency' with a valid string", async () => {
            const db = getFirestore(myAuth);
            const testDoc = db.collection('users').doc(myId);
            await firebase.assertSucceeds(testDoc.update({ currency: "USD" }));
        });
    
        it("Users cannot update 'currency' with invalid data type", async () => {
            const db = getFirestore(myAuth);
            const testDoc = db.collection('users').doc(myId);
            await firebase.assertFails(testDoc.update({ currency: 123 }));
        });
    
        it("Users can update 'deviceInfo' with a valid string", async () => {
            const db = getFirestore(myAuth);
            const testDoc = db.collection('users').doc(myId);
            await firebase.assertSucceeds(testDoc.update({ deviceInfo: "Device Info" }));
        });
    
        it("Users cannot update 'deviceInfo' with invalid data type", async () => {
            const db = getFirestore(myAuth);
            const testDoc = db.collection('users').doc(myId);
            await firebase.assertFails(testDoc.update({ deviceInfo: 12345 }));
        });
    
        it("Users can update 'dataVersion' with a valid number", async () => {
            const db = getFirestore(myAuth);
            const testDoc = db.collection('users').doc(myId);
            await firebase.assertSucceeds(testDoc.update({ dataVersion: 2 }));
        });
    
        it("Users cannot update 'dataVersion' with invalid data type", async () => {
            const db = getFirestore(myAuth);
            const testDoc = db.collection('users').doc(myId);
            await firebase.assertFails(testDoc.update({ dataVersion: "two" }));
        });
    });

    // Deleting PII Fields Tests
    it("Users can nullify their PII fields", async () => {
        // First, set up the profile.
        const admin = getAdminFirestore();
        await admin.collection('users').doc(myId).set({
            userId: myId,
            epochCreated: 1234567890,
            epochLastUpdated: 1234567890,
            epochLastSeen: 1234567890,
            isAnonymous: false,
            loginType: "firebase/google",
            email: "user@example.com",
            deviceInfo: "device_info",
            currency: "USD",
            newsletter: true,
            flags: ["flag1"],
            receiveNotifications: true
        });

        const piiFields = ['email', 'deviceInfo', 'currency', 'newsletter', 'flags', 'receiveNotifications'];
        const db = getFirestore(myAuth);
        const testDoc = db.collection('users').doc(myId);
        let updateData = {};
        piiFields.forEach(field => updateData[field] = null);
        await firebase.assertSucceeds(testDoc.update(updateData));
    });

    // Disallowed Operations Tests
    it("Users cannot delete their own profile", async () => {
        await initializeUserDoc(myId);
        const db = getFirestore(myAuth);
        const testDoc = db.collection('users').doc(myId);
        await firebase.assertFails(testDoc.delete());
    });

    it("Users cannot delete other users' profiles", async () => {
        // First, set up the document.
        const admin = getAdminFirestore();
        await admin.collection('users').doc(theirId).set({
            userId: theirId,
            epochCreated: 1234567890,
            epochLastUpdated: 1234567890,
            epochLastSeen: 1234567890,
            isAnonymous: false,
            loginType: "firebase/google"
        });
        const db = getFirestore(myAuth);
        const testDoc = db.collection('users').doc(theirId);
        await firebase.assertFails(testDoc.delete());
    });

    it("Users cannot write to other users' profiles", async () => {
        const db = getFirestore(myAuth);
        const testDoc = db.collection('users').doc(theirId);
        await firebase.assertFails(testDoc.set({
            userId: theirId,
            epochCreated: 1234567890,
            epochLastUpdated: 1234567890,
            epochLastSeen: 1234567890,
            isAnonymous: false,
            loginType: "firebase/google"
        }));
    });

    // Validation of Data Types and Constraints
    it("Users must provide valid data types for fields", async () => {
        const db = getFirestore(myAuth);
        const testDoc = db.collection('users').doc(myId);
        // Attempt to set a number in a field that expects a string
        await firebase.assertFails(testDoc.set({ email: 12345 }, { merge: true }));
    });

    it("Users cannot provide extra fields not defined in the schema", async () => {
        const db = getFirestore(myAuth);
        const testDoc = db.collection('users').doc(myId);
        await firebase.assertFails(testDoc.set({ extraField: "should not be allowed" }, { merge: true }));
    });

    // Tests for Array Union and Array Remove Operations
    it("Users can add to 'favoriteIds' using arrayUnion", async () => {
        // First, ensure the 'favoriteIds' field exists
        await initializeUserDoc(myId, { favoriteIds: ["id1"] });
        const db = getFirestore(myAuth);
        const testDoc = db.collection('users').doc(myId);
        await firebase.assertSucceeds(testDoc.update({
            favoriteIds: firebase.firestore.FieldValue.arrayUnion("id2")
        }));
    });

    it("Users can remove from 'favoriteIds' using arrayRemove", async () => {
        // First, ensure the 'favoriteIds' field exists
        await initializeUserDoc(myId, { favoriteIds: ["id1", "id2"] });
        const db = getFirestore(myAuth);
        const testDoc = db.collection('users').doc(myId);
        await firebase.assertSucceeds(testDoc.update({
            favoriteIds: firebase.firestore.FieldValue.arrayRemove("id1")
        }));
    });

    it("Users can add to 'followingIds' using arrayUnion", async () => {
        await initializeUserDoc(myId, { followingIds: ["id1"] });
        const db = getFirestore(myAuth);
        const testDoc = db.collection('users').doc(myId);
        await firebase.assertSucceeds(testDoc.update({
            followingIds: firebase.firestore.FieldValue.arrayUnion("id2")
        }));
    });

    it("Users can remove from 'followingIds' using arrayRemove", async () => {
        await initializeUserDoc(myId, { followingIds: ["id1", "id2"] });
        const db = getFirestore(myAuth);
        const testDoc = db.collection('users').doc(myId);
        await firebase.assertSucceeds(testDoc.update({
            followingIds: firebase.firestore.FieldValue.arrayRemove("id1")
        }));
    });

    it("Users can add to 'purchaseRecords' using arrayUnion", async () => {
        await initializeUserDoc(myId, { purchaseRecords: ["purchase1"] });
        const db = getFirestore(myAuth);
        const testDoc = db.collection('users').doc(myId);
        await firebase.assertSucceeds(testDoc.update({
            purchaseRecords: firebase.firestore.FieldValue.arrayUnion("purchase2")
        }));
    });

    it("Users can remove from 'purchaseRecords' using arrayRemove", async () => {
        await initializeUserDoc(myId, { purchaseRecords: ["purchase1", "purchase2"] });
        const db = getFirestore(myAuth);
        const testDoc = db.collection('users').doc(myId);
        await firebase.assertSucceeds(testDoc.update({
            purchaseRecords: firebase.firestore.FieldValue.arrayRemove("purchase1")
        }));
    });

    it("Users can add to 'wallpaperDownloadEvents' using arrayUnion", async () => {
        await initializeUserDoc(myId, { wallpaperDownloadEvents: ["event1"] });
        const db = getFirestore(myAuth);
        const testDoc = db.collection('users').doc(myId);
        await firebase.assertSucceeds(testDoc.update({
            wallpaperDownloadEvents: firebase.firestore.FieldValue.arrayUnion("event2")
        }));
    });
});
