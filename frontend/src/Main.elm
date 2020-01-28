module Main exposing (Resource, Status, main, msgDecoder, resourceDecoder, statusDecoder)

import Browser
import Element
    exposing
        ( Attribute
        , Color
        , Element
        , alignRight
        , column
        , el
        , fill
        , fillPortion
        , height
        , layout
        , maximum
        , none
        , padding
        , rgb255
        , row
        , spacing
        , table
        , text
        , width
        )
import Element.Background as Background
import Element.Border as Border
import Element.Font as Font
import Element.Input as Input
import Html
import Http exposing (Error(..))
import Json.Decode as D
import Time



-- MAIN


main : Program Flags Model Msg
main =
    Browser.element
        { init = init
        , update = update
        , subscriptions = subscriptions
        , view = view
        }



-- MODEL


type ServerState
    = Loading
    | LoadFailed String
    | Loaded Status


type alias Model =
    { state : ServerState
    , url : String
    }


type alias Flags =
    String


type alias Resource =
    { id : String
    , name : String
    , tipe : String
    , state : String
    , isAvailable : Bool
    }


type alias Status =
    { clock : String
    , canExtend : Bool
    , remaining : Maybe String
    , weekdayStartMessage : String
    , resources : List Resource
    }


type alias Cell =
    { content : String
    , attributes : List (Attribute Msg)
    }


loadState : String -> Cmd Msg
loadState url =
    Http.get
        { url = url ++ "status"
        , expect = Http.expectString GotUpdate
        }


extend : String -> Cmd Msg
extend url =
    Http.get
        { url = url ++ "extend"
        , expect = Http.expectString GotUpdate
        }


init : Flags -> ( Model, Cmd Msg )
init flags =
    ( { state = Loading
      , url = flags
      }
    , loadState flags
    )



-- UPDATE


type Msg
    = GotUpdate (Result Http.Error String)
    | GetUpdate Time.Posix
    | Extend


toString : Http.Error -> String
toString e =
    case e of
        BadUrl msg ->
            "Bad url: " ++ msg

        Timeout ->
            "Timeout"

        NetworkError ->
            "Network error"

        BadStatus x ->
            "Bad status: " ++ String.fromInt x

        BadBody msg ->
            "Bad body: " ++ msg


update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    case msg of
        GotUpdate result ->
            case result of
                Ok json ->
                    case msgDecoder json of
                        Ok status ->
                            ( { model | state = Loaded status }, Cmd.none )

                        Err x ->
                            ( { model | state = LoadFailed <| D.errorToString x }, Cmd.none )

                Err x ->
                    ( { model | state = LoadFailed <| toString x }, Cmd.none )

        GetUpdate _ ->
            ( model, loadState model.url )

        Extend ->
            ( model, extend model.url )



-- SUBSCRIPTIONS


subscriptions : Model -> Sub Msg
subscriptions _ =
    Time.every 5000 GetUpdate


blue : Color
blue =
    rgb255 100 100 255


green : Color
green =
    rgb255 75 255 75


red : Color
red =
    rgb255 255 75 75


dark : Color
dark =
    rgb255 20 20 20


grey : Color
grey =
    rgb255 130 130 130


render : Cell -> Element Msg
render cell =
    text cell.content |> el cell.attributes


header : String -> Cell
header content =
    Cell content [ Font.size 20, Font.color blue ]


value : String -> Cell
value content =
    Cell content []


resourceTable : Status -> Element Msg
resourceTable status =
    let
        running =
            case status.remaining of
                Just _ ->
                    True

                Nothing ->
                    False
    in
    table
        [ spacing 10
        , padding 10
        , width (fill |> maximum 950)
        , Font.size 18
        ]
        { data = status.resources
        , columns =
            [ { header = header "Name" |> render
              , width = fillPortion 2
              , view = \r -> value r.name |> color running r |> render
              }
            , { header = header "Type" |> render
              , width = fillPortion 1
              , view = \r -> value r.tipe |> color running r |> render
              }
            , { header = header "State" |> render
              , width = fillPortion 1
              , view = \r -> value r.state |> color running r |> render
              }
            , { header = none
              , width = fillPortion 1
              , view = \_ -> none
              }
            ]
        }


color : Bool -> Resource -> Cell -> Cell
color running resource base =
    let
        c =
            if running && resource.isAvailable then
                green

            else if not running && not resource.isAvailable then
                grey

            else
                red
    in
    { base | attributes = Font.color c :: base.attributes }


extendButton : Bool -> Element Msg
extendButton canExtend =
    if canExtend then
        Input.button
            [ alignRight
            , Border.width 0
            , Border.rounded 3
            , padding 5
            ]
            { onPress = Just Extend, label = text ">" }

    else
        none


defaultPage : List (Element Msg) -> Element Msg
defaultPage content =
    column
        [ Font.color grey, spacing 20, padding 20, width fill, height fill ]
        content


errorPage : String -> Element Msg
errorPage message =
    defaultPage [ title "", el [] (text message) ]


title : String -> Element Msg
title clock =
    row [ width fill ]
        [ el [ Font.size 40 ] <| text "Scheduler"
        , el [ Font.size 14, Font.alignRight, width (fill |> maximum 610) ] <|
            text clock
        ]


page : Status -> Element Msg
page status =
    let
        remainingMsg =
            case status.remaining of
                Just r ->
                    el [ Font.color green ] <| text <| "Running: " ++ r

                Nothing ->
                    el [ Font.color grey ] <| text <| "Not running"
    in
    defaultPage
        [ title status.clock
        , el [ Font.size 14 ] <| text status.weekdayStartMessage
        , row []
            [ remainingMsg
            , extendButton status.canExtend
            ]
        , resourceTable status
        ]


defaultStyle : List (Attribute msg)
defaultStyle =
    [ Background.color dark
    , Font.color grey
    ]


resourceDecoder : D.Decoder Resource
resourceDecoder =
    D.map5 Resource
        (D.field "id" D.string)
        (D.field "name" D.string)
        (D.field "type" D.string)
        (D.field "state" D.string)
        (D.field "isAvailable" D.bool)


decodeResources : Maybe (List Resource) -> D.Decoder (List Resource)
decodeResources rs =
    D.succeed (Maybe.withDefault [] rs)


statusDecoder : D.Decoder Status
statusDecoder =
    D.map5 Status
        (D.field "clock" D.string)
        (D.field "canExtend" D.bool)
        (D.maybe <| D.field "remaining" D.string)
        (D.field "weekdayStartMessage" D.string)
        (D.maybe (D.field "resources" <| D.list resourceDecoder) |> D.andThen decodeResources)


msgDecoder : String -> Result D.Error Status
msgDecoder json =
    D.decodeString statusDecoder json


view : Model -> Html.Html Msg
view model =
    case model.state of
        LoadFailed msg ->
            layout defaultStyle <| errorPage <| "Unable to retrieve namespace data: " ++ msg

        Loading ->
            layout defaultStyle <| errorPage "Loading..."

        Loaded status ->
            layout defaultStyle <| page status
