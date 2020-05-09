# Get Legend Graph

Get the graph information for the saved legend.

**URL** : `/api/{legendId}/graph`

**Method** : `GET`

## Success Response

**Code** : `200 OK`

**Content example**

```json
{
  "nodes": [
    {
      "name": "$x",
      "id": "V45248",
      "type": "entity",
      "category": "node"
    },
    {
      "name": "Eric",
      "id": "V8208",
      "type": "attribute",
      "category": "data"
    },
    {
      "name": "$l",
      "id": "V53440",
      "type": "entity",
      "category": "linked-list"
    },
    {
      "name": "$alexis",
      "id": "V49344",
      "type": "entity",
      "category": "node"
    }
  ],
  "links": [
    {
      "source": 0,
      "target": 1,
      "name": "$x_data",
      "type": "attribute"
    },
    {
      "source": 0,
      "target": 3,
      "name": "$r",
      "type": "relation"
    },
    {
      "source": 2,
      "target": 0,
      "name": "$r",
      "type": "relation"
    },
    {
      "source": 2,
      "target": 3,
      "name": "$r",
      "type": "relation"
    }
  ]
}
```

## Error Response

**Condition** : Legend does not exist.

**Code** : `404 NOT FOUND`
