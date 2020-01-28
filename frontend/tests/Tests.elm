module Tests exposing (beautiful, beautifulJson, bold, boldJson, creepy, creepyJson, jsonList, jsonMessage, msg, one, several)

import Expect
import Json.Decode as D
import Main exposing (Resource, Status, msgDecoder, resourceDecoder)
import Test exposing (Test, test)


boldJson : String
boldJson =
    """
        {
            "id": "id-2387",
            "name": "bold",
            "type": "EC2",
            "state": "running",
            "isAvailable": true
        }
    """


beautifulJson : String
beautifulJson =
    """
        {
            "id": "id-9375",
            "name": "beautiful",
            "type": "ASG",
            "state": "0/2 instances",
            "isAvailable": false
        }
    """


creepyJson : String
creepyJson =
    """
        {
            "id": "id-9375",
            "name": "creepy",
            "type": "RDS",
            "state": "available",
            "isAvailable": true
        }
    """


jsonList : String
jsonList =
    "[" ++ boldJson ++ "," ++ beautifulJson ++ "," ++ creepyJson ++ "]"


jsonMessage : String
jsonMessage =
    """
        {
            "clock": "9:25pm UTC",
            "canExtend": true,
            "weekdayStartMessage": "Auto start disabled",
            "resources": """ ++ jsonList ++ """
        }
    """


creepy : Resource
creepy =
    { name = "creepy"
    , id = "id-9375"
    , tipe = "RDS"
    , state = "available"
    , isAvailable = True
    }


beautiful : Resource
beautiful =
    { name = "beautiful"
    , id = "id-9375"
    , tipe = "ASG"
    , state = "0/2 instances"
    , isAvailable = False
    }


bold : Resource
bold =
    { name = "bold"
    , id = "id-2387"
    , tipe = "EC2"
    , state = "running"
    , isAvailable = True
    }


msg : Test
msg =
    test "Decode full status message" <|
        \_ ->
            Expect.equal
                (msgDecoder jsonMessage)
            <|
                Ok
                    { clock = "9:25pm UTC"
                    , canExtend = True
                    , remaining = Nothing
                    , weekdayStartMessage = "Auto start disabled"
                    , resources = [ bold, beautiful, creepy ]
                    }


several : Test
several =
    test "Decode list of namespaces from JSON" <|
        \_ ->
            Expect.equal
                (D.decodeString (D.list resourceDecoder) jsonList)
                (Ok [ bold, beautiful, creepy ])


one : Test
one =
    test "Decode namespace from JSON" <|
        \_ ->
            Expect.equal
                (D.decodeString resourceDecoder beautifulJson)
                (Ok beautiful)
