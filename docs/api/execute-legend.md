# Execute Legend

Execute a legend.

**URL** : `/api/legend/execute`

**Method** : `POST`

**Data example**

```json
{
  "query": "define\nman sub entity, has name;\nname sub attribute, datatype string;\n\ninsert\n$m isa man, has name \"Brandon\";\n\nmatch\n$m isa man, has name $n;\nget;",
  "queryOptions": {
    "includeAnonymousVariables": false,
    "displayOptions": {
      "entityNamingScheme": "BY_VARIABLE",
      "relationNamingScheme": "BY_VARIABLE",
      "attributeNamingScheme": "BY_VALUE"
    }
  }
}
```

## Success Response

**Code** : `200 OK`

**Content example**

```json
{
  "nodes": [
    {
      "name": "Brandon",
      "id": "V12512",
      "type": "attribute",
      "category": "name"
    },
    {
      "name": "$m",
      "id": "V12304",
      "type": "entity",
      "category": "man"
    }
  ],
  "links": [
    {
      "source": 1,
      "target": 0,
      "name": "$n",
      "type": "attribute"
    }
  ]
}
```
