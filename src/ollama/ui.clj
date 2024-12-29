(ns ollama.ui
  (:gen-class)
  (:require [cljfx.api :as fx]
           [clojure.core.async :as async]
           [clojure.java.io :as io]
           [pyjama.core]
           [pyjama.state]
           [pyjama.image])
  (:import (javafx.scene.image Image)
           (javafx.scene.input TransferMode)))

(def URL (or (System/getenv "OLLAMA_URL") "http://localhost:11434"))
(def model "llama3.2")

(defonce *state
         (atom {:url        URL
                :model      model
                :processing false
                :images []
                :models     []
                :prompt     "Why is the sky blue?"
                :response   ""}))

(def spinner-image
  (Image. (io/input-stream (io/resource "spinner.gif"))))

(def reload-image
 (Image. (io/input-stream (io/resource "reload.png"))))

(def reloading-image
 (Image. (io/input-stream (io/resource "reload.gif"))))

(defn preview-images []
  (into []
        (map
    #(hash-map :fx/type    :image-view  :fit-width  48 :fit-height 48
     :image (Image. (io/input-stream %1)))
    (:images @*state)
  )))

(defn handle-drag []
  (fn [event]
    (let [dragboard (.getDragboard event)
          files (.getFiles dragboard)]
      (when (not-empty files)
        (swap! *state assoc :images (map #(.toString %) files))))))

(defn handle-clear-image [_]
  (swap! *state assoc :images []))

(defmulti event-handler :event/type)

(defmethod event-handler ::close [_]
  (pyjama.state/handle-submit *state))

;
;(def menu-bar
;  {:fx/type :menu-bar
;   :menus [{:fx/type :menu
;            :text "File"
;            :items [{:fx/type :menu-item
;                     :text "Open"
;                     :on-action {:event/type :menu-item/open}}
;                    {:fx/type :menu-item
;                     :text "Exit"
;                     :on-action {:event/type :menu-item/exit}}]}
;           {:fx/type :menu
;            :text "Edit"
;            :items [{:fx/type :menu-item
;                     :text "Undo"
;                     :on-action {:event/type :menu-item/undo}}
;                    {:fx/type :menu-item
;                     :text "Redo"
;                     :on-action {:event/type :menu-item/redo}}]}]})

(defn create-ui [state]
  {:fx/type :stage
   :showing true
   :title   "Pyjama UI"
   :width   600
   :min-width 600
   :height  800
   :min-height 800
   :on-close-request (fn [_] (System/exit 0))
   :icons   [(Image. (io/input-stream (io/resource "delicious.png")))]
   :scene   {:fx/type      :scene
             :stylesheets  #{"styles.css"}
             :accelerators {[:escape] {:event/type ::close}}
             :root         {:fx/type  :v-box
                            :spacing  10
                            :padding  10
                            :children [
                                       ;menu-bar
                                       {:fx/type  :h-box
                                        :spacing  10
                                        :children [{:fx/type :label
                                                    :text    "URL:"}
                                                   {:fx/type         :text-field
                                                    :text            (:url state)
                                                    :on-text-changed #(do
                                                                       (swap! *state assoc :url %)
                                                                       (async/thread (pyjama.state/local-models *state)))}
                                                   ]}
                                       {:fx/type  :h-box
                                        :spacing  10
                                        :children [{:fx/type :label
                                                    :text    "Model:"}
                                                   {:fx/type          :combo-box
                                                    :items            (:local-models state)
                                                    :value            (:model state)
                                                    :on-value-changed #(swap! *state assoc :model %)}
                                                   (if (not (:loading @*state))
                                                   {:fx/type    :image-view
                                                    :image      reload-image
                                                    :fit-width  24
                                                    :fit-height 24
                                                    :on-mouse-clicked #(async/thread
                                                                        (swap! *state assoc :loading true)
                                                                        ; keep for now
                                                                        (println %)
                                                                        (pyjama.state/local-models *state)
                                                                        (swap! *state assoc :loading false)
                                                                        )
                                                    }
                                                   {:fx/type    :image-view
                                                    :image      reloading-image
                                                    :fit-width  24
                                                    :fit-height 24
                                                    })
                                                   ]}
                                       {:fx/type :label
                                        :text    "Prompt:"}
                                       {:fx/type         :text-area
                                        :text            (:prompt state)
                                        :on-text-changed #(swap! *state assoc :prompt %)}
                                       {:fx/type  :h-box
                                        :spacing  30
                                        :children [
                                                   {:fx/type         :h-box
                                                    :spacing         10
                                                    :on-drag-over    (fn [e]
                                                                      (.acceptTransferModes e (into-array [TransferMode/COPY])))
                                                    :on-drag-dropped (handle-drag)
                                                    :children        (if (not (empty? (:images @*state)))
                                                                      (conj
                                                                       (preview-images)
                                                                       {:fx/type   :button
                                                                        :text      "Clear Images"
                                                                        :on-action #(handle-clear-image %)})

                                                                      [{:fx/type :label
                                                                        :text    "Drag and drop an image here"}
                                                                       ])
                                                    }
                                                   ]}


                                       (if (:processing @*state)
                                        {:fx/type    :image-view
                                         :image      spinner-image
                                         :fit-width  24
                                         :fit-height 24}
                                        {:fx/type  :h-box
                                         :spacing  30
                                         :children [
                                                    {:fx/type   :button
                                                     :text      "Ask"
                                                     :on-action (fn [_] (pyjama.state/handle-submit *state))
                                                     }
                                        {
                                         :fx/type :label
                                         :text    "Idle"
                                         }]}
                                        )
                                       {
                                        :fx/type :label
                                        :text    "Response:"}
                                       {:fx/type   :text-area
                                        :wrap-text true
                                        :text      (:response state)
                                        :editable  false}]}}})

(def renderer
 (fx/create-renderer
  :middleware (fx/wrap-map-desc create-ui)
  :opts {:fx.opt/map-event-handler event-handler
         :app-state                *state}))

(defn -main [& args]
 (pyjama.state/local-models *state)
 (fx/mount-renderer *state renderer))
