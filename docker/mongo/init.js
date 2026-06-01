db = db.getSiblingDB("enersight_app");

db.createUser({
  user: "app_user",
  pwd: "app_password",
  roles: [
    {
      role: "readWrite",
      db: "enersight_app"
    }
  ]
});

db.createCollection("training");

print("enersight database initialized");