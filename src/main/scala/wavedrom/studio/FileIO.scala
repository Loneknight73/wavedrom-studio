package wavedrom.studio

import org.scalajs.dom
import org.scalajs.dom.html
import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

// `handle` is a FileSystemFileHandle where the browser provides one
case class OpenedFile(name: String, text: String, handle: Option[js.Any])
case class SavedFile(name: String, handle: Option[js.Any])

// Reading and writing local files. Uses the File System Access API where the
// browser has it (so Save writes back to the opened file), and a file input
// plus a download otherwise.
object FileIO {

  private def window = dom.window.asInstanceOf[js.Dynamic]

  private def pickerTypes = js.Array(
    js.Dynamic.literal(
      description = "WaveJSON",
      accept = js.Dynamic.literal("application/json" -> js.Array(".json"))
    )
  )

  private def await[T](promise: js.Dynamic): Future[T] =
    promise.asInstanceOf[js.Promise[T]].toFuture

  // Dismissing a file picker rejects its promise with an AbortError
  private def isAbort(error: Throwable): Boolean = error match {
    case js.JavaScriptException(e) =>
      s"${e.asInstanceOf[js.Dynamic].name}" == "AbortError"
    case _ => false
  }

  // Resolves to the chosen files, or to nothing when the picker is dismissed
  def open(fallbackInput: html.Input): Future[Seq[OpenedFile]] = {
    if (js.typeOf(window.showOpenFilePicker) == "function") {
      val options = js.Dynamic.literal(multiple = true, types = pickerTypes)
      await[js.Array[js.Dynamic]](window.showOpenFilePicker(options))
        .flatMap { handles =>
          Future.sequence(handles.toSeq.map { handle =>
            for {
              file <- await[js.Dynamic](handle.getFile())
              text <- await[String](file.text())
            } yield OpenedFile(s"${handle.name}", text, Some(handle))
          })
        }
        .recover { case e if isAbort(e) => Seq.empty }
    } else openWithInput(fallbackInput)
  }

  private def openWithInput(input: html.Input): Future[Seq[OpenedFile]] = {
    val chosen = Promise[Seq[dom.File]]()
    val onCancel: js.Function1[dom.Event, Unit] = _ => chosen.trySuccess(Seq.empty)
    input.value = ""
    input.onchange = (_: dom.Event) => {
      val files = input.files
      chosen.trySuccess((0 until files.length).map(files(_)))
    }
    input.asInstanceOf[js.Dynamic].oncancel = onCancel
    input.click()

    chosen.future.flatMap { files =>
      Future.sequence(files.map { file =>
        await[String](file.asInstanceOf[js.Dynamic].text())
          .map(text => OpenedFile(file.name, text, None))
      })
    }
  }

  // Writes to `handle` when there is one; otherwise asks where to save, or
  // downloads the file. Resolves to None when the picker is dismissed.
  def save(
      suggestedName: String,
      handle: Option[js.Any],
      text: String
  ): Future[Option[SavedFile]] = {
    handle match {
      case Some(existing) =>
        write(existing, text).map(_ => Some(SavedFile(suggestedName, handle)))
      case None if js.typeOf(window.showSaveFilePicker) == "function" =>
        val options =
          js.Dynamic.literal(suggestedName = suggestedName, types = pickerTypes)
        await[js.Dynamic](window.showSaveFilePicker(options))
          .flatMap { picked =>
            write(picked, text)
              .map(_ => Option(SavedFile(s"${picked.name}", Some(picked))))
          }
          .recover { case e if isAbort(e) => None }
      case None =>
        download(suggestedName, text)
        Future.successful(Some(SavedFile(suggestedName, None)))
    }
  }

  private def write(handle: js.Any, text: String): Future[Unit] =
    for {
      writable <- await[js.Dynamic](handle.asInstanceOf[js.Dynamic].createWritable())
      _ <- await[Any](writable.write(text))
      _ <- await[Any](writable.close())
    } yield ()

  private def download(fileName: String, text: String): Unit = {
    val blob = new dom.Blob(
      js.Array(text),
      new dom.BlobPropertyBag { `type` = "application/json;charset=utf-8" }
    )
    val url = dom.URL.createObjectURL(blob)
    val link = dom.document.createElement("a").asInstanceOf[html.Anchor]
    link.href = url
    link.asInstanceOf[js.Dynamic].download = fileName
    dom.document.body.appendChild(link)
    link.click()
    dom.document.body.removeChild(link)
    dom.URL.revokeObjectURL(url)
  }

  // Whether two handles point at the same file on disk
  def sameFile(a: js.Any, b: js.Any): Future[Boolean] =
    await[Boolean](a.asInstanceOf[js.Dynamic].isSameEntry(b))
      .recover { case _ => false }
}
