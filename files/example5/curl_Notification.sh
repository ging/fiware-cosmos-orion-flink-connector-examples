while true
do
    temp=$(shuf -i 18-53 -n 1)
    number=$(shuf -i 1-3113 -n 1)

    curl -v -s -S X POST http://localhost:9001 \
    --header 'Content-Type: application/json; charset=utf-8' \
    --header 'Accept: application/json' \
    --header 'User-Agent: orion/0.10.0' \
    --header "Fiware-Service: demo" \
    --header "Fiware-ServicePath: /test" \
    -d  '{
         "data": [
             {
                 "id": "R1",
                 "type": "Node",
                 "information": {
                     "type": "object",
                     "value": {
                        "buses":[
                            {
                                "name": "BusCompany1",
                                "schedule": {
                                    "morning": [7,9,11],
                                    "afternoon": [13,15,17,19],
                                    "night" : [23,1,5]
                                },
                                "price": 19
                            },
                            {
                                "name": "BusCompany2",
                                "schedule": {
                                    "morning": [8,10,12],
                                    "afternoon": [16,20],
                                    "night" : [23]
                                },
                                "price": 14
                            }
                        ]
                     },
                     "metadata": {}
                    }
             }
         ],
         "subscriptionId": "57458eb60962ef754e7c0998"
     }'


    sleep 1
done