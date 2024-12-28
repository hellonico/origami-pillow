(ns ollama.models
  (:require [cljfx.api :as fx]
            [clojure.core.async :as async]
            [pyjama.core]
            [pyjama.models :refer :all]
            [pyjama.state])
  (:import (javafx.scene.control TableRow)
           (javafx.scene.input MouseEvent)))

(def state
  (atom {:query        ""
         :url          "http://localhost:11434"
         :models       []
         :pull         {
                        :status "Idle"
                        :model  ""
                        }
         :local-models []}))


;; Define your function to handle double-click events
(defn your-function [model]
  (println "Handling model:" model)
  ;; Add your logic here
  )


(defmulti event-handler :event/type)

(defmethod event-handler :default [e]
  (prn (:event/type e) (:fx/event e) (dissoc e :fx/context :fx/event :event/type)))

(defmethod event-handler ::on-cell-click [event]
  (when (= 2 (.getClickCount event))
    (println event)))

(defn description-cell-factory []
  (fn [description]
    {:text (:description description)
     :graphic {:fx/type :text
               :text description
               :wrapping-width 300 ; Adjust this to the desired column width
               :style "-fx-wrap-text: true;"}}))


(defn table-view [{:keys [models query local-models]}]
  {:fx/type     :v-box
   :v-box/vgrow :always
   :children    [{:fx/type     :h-box
                  :alignment   :center-left
                  :spacing     10
                  :max-height  50
                  :v-box/vgrow :always
                  :children    [{:fx/type :label
                                 :text    "Search:"}
                                {:fx/type         :text-field
                                 :text            query
                                 :on-text-changed #(swap! state assoc :query %)}
                                {:fx/type :label
                                 :text    "Status:"}
                                {:fx/type :label
                                 :text    (str (get-in @state [:pull :model]) " > " (get-in @state [:pull :status]))}]}
                 {:fx/type     :table-view
                  :v-box/vgrow :always
                  :items       (filter-models models query)
                  :row-factory {:fx/cell-type :table-row
                                :describe     (fn [row-data]
                                                {
                                                 :on-key-pressed {:event/type :row-key :row row-data}
                                                 :on-mouse-clicked
                                                 {:event/type :row-click
                                                  :row        row-data}})}
                  ;:row-factory {:fx/cell-type :table-row
                  ;               :describe (fn [row-data]
                  ;                           {:on-mouse-clicked
                  ;                            (fn [event]
                  ;                              (println event)
                  ;                              (when (= 2 (:click-count event)) ; Check for double-click
                  ;                                {:event/type ::row-double-click
                  ;                                 :row row-data}))})}
                  :columns     [{:fx/type            :table-column
                                 :text               "Name"
                                 :cell-value-factory :name}
                                ;{:fx/type            :table-column
                                ; :text               "Description"
                                ; :cell-value-factory :description}
                                {:fx/type :table-column
                                 :text "Description"
                                 :cell-value-factory :description
                                 :cell-factory {:fx/cell-type :table-cell
                                                :describe (description-cell-factory)}}
                                {:fx/type            :table-column
                                 :text               "Updated"
                                 :cell-value-factory (fn [row] (:updated row))}
                                {:fx/type            :table-column
                                 :text               "Pulls"
                                 :cell-value-factory (fn [row] (:pulls row))}
                                {:fx/type            :table-column
                                 :text               "Remote"
                                 :cell-value-factory (fn [row]
                                                       (if (some #(= (:name row) %) local-models)
                                                         "☑️"
                                                         ""))}]}]
   })

(defmulti handle-event :event/type)

; README: keep those two functions as battle reminder ;)

(defmethod handle-event :default [e]
  (prn (:event/type e) (:fx/event e) (dissoc e :fx/context :fx/event :event/type)))

;(defn map-event-handler [e]
;  (prn (:event/type e) (:fx/event e) (dissoc e :fx/context :fx/event :event/type)))

(defmethod handle-event :row-click [event]
  (let [row-data (:row event)]
    (pyjama.state/pull-model-stream state (:name row-data))))

;(defmethod handle-event :row-key [event]
;  (let [row-data (:row event)]
;  (println "row key" row-data)))


;; The main application view
(defn app-view [state]
  {:fx/type          :stage
   :showing          true
   :title            "Pyjama Models"
   :width            1200
   :min-width        1200
   :height           700
   :min-height       700
   :on-close-request (fn [_] (System/exit 0))
   :scene            {:fx/type      :scene
                      :stylesheets  #{"styles.css"}
                      :accelerators {[:escape] {:event/type ::close}}
                      :root         {:fx/type  :v-box
                                     :spacing  10
                                     :padding  10
                                     :children [(table-view state)]}}})

(def renderer
  (fx/create-renderer
    :middleware (fx/wrap-map-desc assoc :fx/type app-view)
    :opts {:state                    state
           :fx.opt/map-event-handler handle-event}))

(defn -main []
  (pyjama.state/local-models state)
  (pyjama.state/remote-models state)
  (fx/mount-renderer state renderer))