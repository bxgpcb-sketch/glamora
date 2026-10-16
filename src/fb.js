// Firebase setup + a small adapter so the app code can use simple
// db.collection(...).where(...).onSnapshot(...) / db.doc(...).set(...) calls.
import { initializeApp } from "firebase/app";
import {
  getAuth, onAuthStateChanged, signInWithEmailAndPassword, signOut,
  sendPasswordResetEmail, updatePassword, EmailAuthProvider, reauthenticateWithCredential,
} from "firebase/auth";
import {
  initializeFirestore, persistentLocalCache, persistentMultipleTabManager,
  collection, doc, query, where, onSnapshot, getDocs, getDoc, addDoc, setDoc, updateDoc, deleteDoc,
} from "firebase/firestore";

const firebaseConfig = {
  apiKey: "AIzaSyD2-uXZ8_AsSmLiBgUKSvzn2A0OTMjmnwY",
  authDomain: "glamora-fc7b4.firebaseapp.com",
  projectId: "glamora-fc7b4",
  storageBucket: "glamora-fc7b4.firebasestorage.app",
  messagingSenderId: "450575356393",
  appId: "1:450575356393:web:a8c5e97eca6686092cfb78",
};

const app = initializeApp(firebaseConfig);
export const auth = getAuth(app); // default persistence = stays signed in on this device

let fs;
try {
  fs = initializeFirestore(app, { localCache: persistentLocalCache({ tabManager: persistentMultipleTabManager() }) });
} catch (e) {
  fs = initializeFirestore(app, {});
}

const wrapDoc = (s) => ({ id: s.id, exists: s.exists(), data: () => s.data(), fromCache: s.metadata.fromCache });
const wrapQuery = (s) => ({ docs: s.docs.map(wrapDoc), size: s.size, empty: s.empty, fromCache: s.metadata.fromCache });

function q(ref, filters) {
  const target = () => (filters.length ? query(ref, ...filters) : ref);
  return {
    where: (f, op, v) => q(ref, [...filters, where(f, op, v)]),
    onSnapshot: (next, err) => onSnapshot(target(), (s) => next(wrapQuery(s)), err),
    get: async () => wrapQuery(await getDocs(target())),
  };
}
function d(ref) {
  return {
    id: ref.id,
    get: async () => wrapDoc(await getDoc(ref)),
    set: (data) => setDoc(ref, data),
    update: (data) => updateDoc(ref, data),
    delete: () => deleteDoc(ref),
    onSnapshot: (next, err) => onSnapshot(ref, (s) => next(wrapDoc(s)), err),
  };
}
export const db = {
  collection(path) {
    const ref = collection(fs, path);
    return { ...q(ref, []), doc: (id) => d(id ? doc(ref, id) : doc(ref)), add: async (data) => d(await addDoc(ref, data)) };
  },
  doc: (path) => d(doc(fs, path)),
};

export const authApi = {
  onChange: (fn) => onAuthStateChanged(auth, fn),
  signIn: (email, pass) => signInWithEmailAndPassword(auth, email, pass),
  signOut: () => signOut(auth),
  refresh: () => (auth.currentUser ? auth.currentUser.getIdToken(true) : Promise.resolve(null)),
  resetPassword: (email) => sendPasswordResetEmail(auth, email),
  async changePassword(current, next) {
    const u = auth.currentUser;
    await reauthenticateWithCredential(u, EmailAuthProvider.credential(u.email, current));
    await updatePassword(u, next);
  },
};
