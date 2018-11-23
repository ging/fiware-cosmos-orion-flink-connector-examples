while true
do
    temp1=$(shuf -i 18-53 -n 1)
    number1=$(shuf -i 1-3113 -n 1)
    temp2=$(shuf -i 18-53 -n 1)
    number2=$(shuf -i 1-3113 -n 1)
    curl -v -s -S X POST http://localhost:9001 \
    --header 'Content-Type: application/json; charset=utf-8' \
    --header 'Accept: application/json' \
    --header 'User-Agent: orion/0.10.0' \
    --header "Fiware-Service: demo" \
    --header "Fiware-ServicePath: /test" \
    -d  '{
         "data": [
             {
                 "id": "R1","type": "Node",
                 "co": {"type": "Float","value": 0,"metadata": {}},
                 "co2": {"type": "Float","value": 0,"metadata": {}},
                 "humidity": {"type": "Float","value": 40,"metadata": {}},
                 "pressure": {"type": "Float","value": '$number1',"metadata": {}},
                 "temperature": {"type": "Float","value": '$temp1',"metadata": {}},
                 "wind_speed": {"type": "Float","value": 1.06,"metadata": {}}
             },
             {
                  "id": "R2","type": "Node",
                  "co": {"type": "Float","value": 0,"metadata": {}},
                  "co2": {"type": "Float","value": 0,"metadata": {}},
                  "humidity": {"type": "Float","value": 40,"metadata": {}},
                  "pressure": {"type": "Float","value": '$number2',"metadata": {}},
                  "temperature": {"type": "Float","value": '$temp2',"metadata": {}},
                  "wind_speed": {"type": "Float","value": 1.06,"metadata": {}}
              }
         ],
         "subscriptionId": "57458eb60962ef754e7c0998"
     }'


    sleep 1
done