const fs = require('fs');
const path = require('path');

db.createUser(
    {
        user: "food4future",
        pwd: "food4future",
        roles: [
            {
                role: "readWrite",
                db: "food4future"
            }
        ]
    }
);

const initDataPath = '/init_data';
db = db.getSiblingDB('food4future');

const jsonFiles = fs.readdirSync(initDataPath, { withFileTypes: true })
  .filter(file => file.isFile() && path.extname(file.name) === '.json')
  .map(file => file.name);

  jsonFiles.forEach(file => {
    const filePath = path.join(initDataPath, file);
    const fileContent = fs.readFileSync(filePath, 'utf8');
    const collectionName = path.basename(file, '.json');
    db[collectionName].insertMany(JSON.parse(fileContent));
  });