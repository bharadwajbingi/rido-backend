// Split tests into smaller batches
const fs = require('fs');
const original = JSON.parse(fs.readFileSync('postman-collections/Rido_200_Tests.postman_collection.json'));

const batches = [
  { name: 'Batch1_Setup_Registration', folders: ['00-Setup', '01-Registration'] },
  { name: 'Batch2_Login', folders: ['02-Login'] },
  { name: 'Batch3_Protected', folders: ['03-Protected'] },
  { name: 'Batch4_Refresh_JWKS', folders: ['04-Refresh', '05-JWKS'] },
  { name: 'Batch5_Logout_Admin', folders: ['06-Logout', '07-Admin'] },
  { name: 'Batch6_Security_Check', folders: ['08-Security', '09-Check'] },
  { name: 'Batch7_Sessions', folders: ['10-Sessions'] }
];

batches.forEach((batch, i) => {
  const c = {
    info: {
      _postman_id: `rido-batch-${i+1}`,
      name: `Rido Tests - ${batch.name}`,
      schema: original.info.schema
    },
    variable: original.variable,
    item: []
  };
  
  batch.folders.forEach(folderName => {
    const folder = original.item.find(f => f.name && f.name.includes(folderName.split('-')[1] || folderName));
    if (folder) c.item.push(folder);
  });
  
  const count = c.item.reduce((sum, f) => sum + (f.item ? f.item.length : 0), 0);
  fs.writeFileSync(`postman-collections/batches/${batch.name}.json`, JSON.stringify(c, null, 2));
  console.log(`Created ${batch.name}.json with ${count} tests`);
});

console.log('\nAll batches created in postman-collections/batches/');
