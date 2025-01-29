(ns ollama.models
  (:gen-class)
  (:require [cljfx.api :as fx]
            [clojure.core.async :as async]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [pyjama.components]
            [pyjama.core]
            [pyjama.models :refer :all]
            [pyjama.state]
            [pyjama.fx.utils]))

(def state
  (atom {:query           ""
         :url             "http://localhost:11434"
         :models          []
         :show-dialog     false
         :selected        nil
         :query-installed false
         :pull            {
                           :status "Idle"
                           :model  ""
                           }
         :local-models    []}))


(defn async-reconnect []
  (async/thread
    (try
      (pyjama.state/check-connection state)
      (Thread/sleep 500)                                    ; TODO wtf
      (when (get-in @state [:ollama :connected])
        (pyjama.state/local-models state)
        (pyjama.state/remote-models state)))))

(defn description-cell-factory []
  (fn [description]
    {:graphic {:fx/type        :text
               :text           description
               ;:style "-fx-text-fill: hotpink; -fx-fill: hotpink; "
               ;:max-width 200 ; Limit the width of the label
               :wrapping-width 380
               }}))                                         ; Enable wrapping for the label


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
                                 :min-width       400
                                 :text            query
                                 :on-text-changed #(swap! state assoc :query %)}
                                {:fx/type             :check-box
                                 :text                "Installed"
                                 :selected            (@state :query-installed)
                                 :on-selected-changed {:event/type ::toggle-checkbox}}

                                {:fx/type :label :min-width 100 :text "URL"}
                                {:fx/type   :text-field
                                 :min-width 400
                                 :text      (:url @state)
                                 :on-text-changed
                                 {:event/type :url-updated}
                                 }
                                (pyjama.components/connected-image @state)
                                {:fx/type :label
                                 :text    "Status:"}
                                {:fx/type :label
                                 :text    (str (get-in @state [:pull :model]) " > " (get-in @state [:pull :status]))}]}
                 {:fx/type     :table-view
                  :v-box/vgrow :always
                  :items       (let [items (filter-models models query)
                                     filtered (if (:query-installed @state)
                                                (filter
                                                  #(not (empty? (get-installed-sizes (@state :local-models) (:name %)))) items)
                                                items
                                                )
                                     ]
                                 filtered
                                 )
                  :row-factory {:fx/cell-type :table-row
                                :describe     (fn [row-data]
                                                {
                                                 :on-mouse-clicked
                                                 {:event/type :row-click
                                                  :row        row-data}})}
                  :columns     [{:fx/type            :table-column
                                 :text               "Name"
                                 :cell-value-factory :name
                                 :cell-factory       {:fx/cell-type :table-cell
                                                      :describe     (fn [cell-data]
                                                                      {:text (str cell-data)
                                                                       :on-mouse-clicked
                                                                       {:event/type :name-cell-click
                                                                        :cell-data  cell-data}})}}


                                {:fx/type            :table-column
                                 :text               "Description"
                                 :cell-value-factory :description
                                 :cell-factory       {:fx/cell-type :table-cell
                                                      :describe     (description-cell-factory)}}
                                {:fx/type            :table-column
                                 :text               "Updated"
                                 :cell-value-factory (fn [row] (:updated row))}
                                {:fx/type            :table-column
                                 :text               "Pulls"
                                 :cell-value-factory (fn [row] (:pulls row))}
                                {:fx/type            :table-column
                                 :text               "Capability"
                                 :cell-value-factory (fn [row] (:capability row))}
                                {:fx/type            :table-column
                                 :text               "Sizes"
                                 :cell-value-factory (fn [row] (str/join "," (:sizes row)))}
                                {:fx/type            :table-column
                                 :text               "Installed"
                                 :cell-value-factory (fn [row]
                                                       (str/join "," (get-installed-sizes (@state :local-models) (:name row))))}]}]
   })

(defn pull-option [selected option]
  {:fx/type   :button
   :text      option
   :on-action (fn [_]
                (pyjama.state/pull-model-stream state (str selected ":" option))
                (swap! state assoc :selected nil)
                (swap! state assoc :show-dialog false))}
  )

(defn delete-option [selected option]
  {:fx/type   :button
   :text      option
   :on-action (fn [_]
                (pyjama.core/ollama (:url @state) :delete
                                    {:model (:name (@state :selected))} identity)
                (swap! state
                       (fn [current-state]
                         (-> current-state
                             (update-in [:pull :model] (constantly (:name (@state :selected))))
                             (update-in [:pull :status] (constantly "deleted"))
                             (assoc :selected nil)
                             (assoc :show-dialog false)
                             )
                         ))
                )}
  )

(defn dialog-view []
  {:fx/type   :v-box
   :alignment :center
   :spacing   10
   :padding   20
   :children  [{:fx/type :label
                :text    "Model Dialog"}
               {:fx/type     :v-box
                :alignment   :center-left
                :v-box/vgrow :always
                :spacing     10
                :children    [
                              {:fx/type :label :text (:name (@state :selected))}
                              {:fx/type :label :text (:description (@state :selected))}

                              (if (get-in @state [:ollama :connected])
                                {:fx/type     :v-box
                                 :alignment   :center-left
                                 :v-box/vgrow :always
                                 :spacing     10
                                 :children    [

                                               {:fx/type     :h-box
                                                :v-box/vgrow :always
                                                :spacing     10
                                                ;:padding     20
                                                :children
                                                (let [can_delete (get-installed-sizes (:local-models @state) (:name (@state :selected)))]
                                                  (if (> (count can_delete) 0)
                                                    (into [{:fx/type :label :text "Delete:"}]
                                                          (map (partial delete-option (:name (@state :selected)))
                                                               can_delete
                                                               ))
                                                    [{:fx/type :label :text "Model not installed."}]
                                                    ))
                                                }

                                               (if (:pulling @state)
                                                 {:fx/type :label}

                                                 {:fx/type     :h-box
                                                  :v-box/vgrow :always
                                                  ;:alignment :center
                                                  :spacing     10
                                                  ;:padding     20
                                                  :children    (let [selected (:name (@state :selected))
                                                                     sizes (get-sizes (:models @state) selected)
                                                                     options (into [{:fx/type :label :text "Pull:"}]
                                                                                   (map (partial pull-option (:name (@state :selected)))
                                                                                        sizes))]
                                                                 ;(println selected)
                                                                 ;(println sizes)
                                                                 options
                                                                 )
                                                  }
                                                 )

                                               {:fx/type   :button
                                                :text      "Cancel"
                                                :on-action (fn [_]
                                                             (swap! state assoc :selected nil)
                                                             (swap! state assoc :show-dialog false))}

                                               ]}
                                (pyjama.components/connected-image @state))

                              ;]}
                              ]}]})

(defmulti handle-event :event/type)

; README: keep those two functions as battle reminder ;)

(defmethod handle-event :default [e]
  ;(prn (:event/type e) (:fx/event e) (dissoc e :fx/context :fx/event :event/type))
  )

(defmethod handle-event :name-cell-click [event]
  ;(println "Name cell clicked:" (:cell-data event))
  (pyjama.fx/open-url (str "https://ollama.com/library/" (:cell-data event))))

(defmethod handle-event :url-updated [new-url]
  ; (prn "updated url" (@state :url) " " (:fx/event new-url))

  ; TODO reconnect
  (swap! state assoc :local-models [] :url (:fx/event new-url))
  (async-reconnect))

(defmethod handle-event ::toggle-checkbox [_]
  (swap! state update :query-installed not))

;(defn map-event-handler [e]
;  (prn (:event/type e) (:fx/event e) (dissoc e :fx/context :fx/event :event/type)))

(defmethod handle-event :row-click [event]
  (swap! state assoc :selected (:row event))
  (swap! state assoc :show-dialog true))

(defmethod handle-event ::show-dialog [_]
  (swap! state assoc :show-dialog true))

(defmethod handle-event ::on-dialog-close [e]
  (let [button (-> e :fx/event .getButtonData)]
    (swap! state assoc :show-dialog false)))

(defmethod handle-event :row-key [event]
  (println "row key" event))

;; The main application view
(defn app-view [_state]
  {:fx/type          :stage
   :showing          true
   :title            "Pyjama Models"
   :width            1300
   :min-width        1300
   :height           700
   :min-height       700
   :on-close-request (fn [_] (System/exit 0))
   :scene            {:fx/type      :scene
                      ;:stylesheets  #{"styles.css"}
                      :stylesheets  #{
                                      "extra.css"
                                      (.toExternalForm (io/resource "terminal.css"))
                                      }
                      :accelerators {[:escape] {:event/type ::close}}
                      :root         {:fx/type :v-box
                                     :spacing 10
                                     :padding 10
                                     :children
                                     (if (@state :show-dialog)
                                       [(dialog-view)]
                                       [(table-view _state)])}}})

(def renderer
  (fx/create-renderer
    :middleware (fx/wrap-map-desc assoc :fx/type app-view)
    :opts {:state                    state
           :fx.opt/map-event-handler handle-event}))

(defn -main [& args]
  (pyjama.fx.utils/javafx-runtime-version)
  (async-reconnect)
  (pyjama.state/remote-models state)
  (fx/mount-renderer state renderer))